package com.bank.expenselimiter.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A validated incoming transaction, independent of the web layer. */
public record IncomingTransaction(
        String accountFrom,
        String accountTo,
        String currency,
        BigDecimal amount,
        ExpenseCategory category,
        Instant datetime) {
}
