package com.bank.expenselimiter.service;

import com.bank.expenselimiter.domain.ExpenseCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialises everything that reads or changes the spending of one (account, category) pair.
 * Uses a PostgreSQL transaction-level advisory lock: it needs no extra row to lock, works for the very
 * first transaction of an account, and is released automatically on commit/rollback.
 * Must be called inside a transaction.
 */
@Component
public class AccountLock {

    private final JdbcTemplate jdbc;

    public AccountLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String account, ExpenseCategory category) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                (RowCallbackHandler) rs -> { },
                account + ":" + category.name());
    }
}
