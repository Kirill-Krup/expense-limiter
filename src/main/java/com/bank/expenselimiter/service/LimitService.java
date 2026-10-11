package com.bank.expenselimiter.service;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.SpendingLimit;
import com.bank.expenselimiter.exception.LimitAlreadyExistsException;
import com.bank.expenselimiter.repository.SpendingLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Limits are append-only. The effective date is always the server's "now" (from the injected Clock),
 * so a client can neither back-date nor post-date a limit, and there is no update operation at all.
 */
@Service
public class LimitService {

    private final SpendingLimitRepository limits;
    private final AccountLock accountLock;
    private final Clock clock;

    public LimitService(SpendingLimitRepository limits, AccountLock accountLock, Clock clock) {
        this.limits = limits;
        this.accountLock = accountLock;
        this.clock = clock;
    }

    @Transactional
    public SpendingLimit setLimit(String account, ExpenseCategory category, BigDecimal limitSumUsd) {
        accountLock.acquire(account, category);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (limits.existsByAccountAndCategoryAndSetAt(account, category, now)) {
            throw new LimitAlreadyExistsException(
                    "A limit for this account and category was already set at " + now + ". Retry in a moment.");
        }
        return limits.save(SpendingLimit.create(account, category, limitSumUsd, now));
    }

    @Transactional(readOnly = true)
    public List<SpendingLimit> findAll(String account) {
        return limits.findByAccountOrderBySetAtDescIdDesc(account);
    }
}
