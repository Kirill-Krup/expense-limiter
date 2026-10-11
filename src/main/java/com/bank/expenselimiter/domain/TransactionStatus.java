package com.bank.expenselimiter.domain;

public enum TransactionStatus {
    /** USD amount is known, limit_exceeded is final. */
    PROCESSED,
    /** The exchange rate was unavailable; the transaction is stored and will be completed by a retry job. */
    PENDING_RATE
}
