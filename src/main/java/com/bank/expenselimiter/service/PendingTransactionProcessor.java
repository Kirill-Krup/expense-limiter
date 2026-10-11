package com.bank.expenselimiter.service;

import com.bank.expenselimiter.config.AppProperties;
import com.bank.expenselimiter.domain.ExchangeRate;
import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.domain.TransactionStatus;
import com.bank.expenselimiter.repository.ExpenseTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Retries transactions that were stored without a rate because the external API was down.
 * Failure of one transaction never stops the others.
 */
@Service
public class PendingTransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionProcessor.class);

    private final ExpenseTransactionRepository transactions;
    private final ExchangeRateService exchangeRates;
    private final TransactionRegistrar registrar;
    private final BusinessCalendar calendar;
    private final AppProperties properties;

    public PendingTransactionProcessor(ExpenseTransactionRepository transactions, ExchangeRateService exchangeRates,
                                       TransactionRegistrar registrar, BusinessCalendar calendar,
                                       AppProperties properties) {
        this.transactions = transactions;
        this.exchangeRates = exchangeRates;
        this.registrar = registrar;
        this.calendar = calendar;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.pending.retry-interval}",
            initialDelayString = "${app.pending.retry-interval}")
    public void scheduledRun() {
        processPending();
    }

    /** @return how many transactions were completed in this run */
    public int processPending() {
        List<ExpenseTransaction> pending = transactions.findByStatusOrderByIdAsc(
                TransactionStatus.PENDING_RATE, PageRequest.of(0, properties.pending().batchSize()));
        int completed = 0;
        for (ExpenseTransaction tx : pending) {
            try {
                Optional<ExchangeRate> rate = exchangeRates.resolve(tx.getCurrency(), calendar.dayOf(tx.getDatetime()));
                if (rate.isPresent() && registrar.completePending(tx.getId(), rate.get())) {
                    completed++;
                }
            } catch (RuntimeException e) {
                log.error("Could not complete pending transaction {}", tx.getId(), e);
            }
        }
        if (!pending.isEmpty()) {
            log.info("Pending run: {} of {} transactions completed", completed, pending.size());
        }
        return completed;
    }
}
