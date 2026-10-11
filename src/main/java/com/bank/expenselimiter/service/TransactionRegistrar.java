package com.bank.expenselimiter.service;

import com.bank.expenselimiter.config.AppProperties;
import com.bank.expenselimiter.domain.ExchangeRate;
import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.domain.IncomingTransaction;
import com.bank.expenselimiter.domain.LimitEvaluator;
import com.bank.expenselimiter.domain.MonthWindow;
import com.bank.expenselimiter.domain.SpendingLimit;
import com.bank.expenselimiter.domain.TransactionStatus;
import com.bank.expenselimiter.domain.UsdConverter;
import com.bank.expenselimiter.repository.ExpenseTransactionRepository;
import com.bank.expenselimiter.repository.SpendingLimitRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

/**
 * The critical section: under a per-(account, category) lock it reads the month's spending and the limit in
 * force, decides limit_exceeded and stores the transaction. Two parallel requests can therefore never see
 * the same remaining limit.
 */
@Service
public class TransactionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TransactionRegistrar.class);

    private final ExpenseTransactionRepository transactions;
    private final SpendingLimitRepository limits;
    private final AccountLock accountLock;
    private final AppProperties properties;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public TransactionRegistrar(ExpenseTransactionRepository transactions, SpendingLimitRepository limits,
                                AccountLock accountLock, AppProperties properties, Clock clock) {
        this.transactions = transactions;
        this.limits = limits;
        this.accountLock = accountLock;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Stores the transaction. Without a rate (and not in USD) it is stored as PENDING_RATE: data is never lost.
     */
    @Transactional
    public ExpenseTransaction register(IncomingTransaction incoming, Optional<ExchangeRate> rate) {
        accountLock.acquire(incoming.accountFrom(), incoming.category());
        ExpenseTransaction tx = ExpenseTransaction.received(incoming, clock.instant());
        if (CurrencyCodes.isUsd(incoming.currency())) {
            evaluate(tx, null);
        } else if (rate.isPresent()) {
            evaluate(tx, rate.get());
        }
        ExpenseTransaction saved = transactions.save(tx);
        log.info("Transaction {} stored: status={}, limitExceeded={}", saved.getId(), saved.getStatus(), saved.isLimitExceeded());
        return saved;
    }

    /** Completes a PENDING_RATE transaction once its rate became available. */
    @Transactional
    public boolean completePending(long id, ExchangeRate rate) {
        ExpenseTransaction tx = transactions.findById(id).orElseThrow();
        accountLock.acquire(tx.getAccountFrom(), tx.getCategory());
        entityManager.refresh(tx); // state may have changed while we waited for the lock
        if (tx.getStatus() != TransactionStatus.PENDING_RATE) {
            return false;
        }
        evaluate(tx, rate);
        log.info("Pending transaction {} completed, limitExceeded={}", id, tx.isLimitExceeded());
        return true;
    }

    private void evaluate(ExpenseTransaction tx, ExchangeRate rate) {
        BigDecimal amountUsd = rate == null
                ? tx.getAmount()
                : UsdConverter.toUsd(tx.getAmount(), rate.getRate());

        MonthWindow month = MonthWindow.containing(tx.getDatetime(), properties.businessZone());
        BigDecimal spent = Optional.ofNullable(transactions.sumUsd(
                        tx.getAccountFrom(), tx.getCategory(), TransactionStatus.PROCESSED,
                        month.start(), month.endExclusive()))
                .orElse(BigDecimal.ZERO);

        // the limit that was in force when the transaction happened, not the latest one
        Optional<SpendingLimit> limit = limits.findEffective(
                tx.getAccountFrom(), tx.getCategory().name(), tx.getDatetime());
        BigDecimal limitSum = limit.map(SpendingLimit::getLimitSum).orElse(properties.defaultLimitUsd());

        boolean exceeded = LimitEvaluator.isExceeded(spent, amountUsd, limitSum);
        tx.markProcessed(amountUsd, rate, limit.orElse(null), limitSum, exceeded);
    }
}
