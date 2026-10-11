package com.bank.expenselimiter.web.dto;

import com.bank.expenselimiter.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Item 6 of the assignment: the incoming structure plus the limit that was exceeded.
 * {@code limitDatetime} is null when the default limit applied (no limit had been set yet).
 */
public record ExceededTransactionResponse(
        String accountFrom,
        String accountTo,
        String currencyShortname,
        BigDecimal sum,
        ExpenseCategory expenseCategory,
        OffsetDateTime datetime,
        BigDecimal limitSum,
        OffsetDateTime limitDatetime,
        String limitCurrencyShortname) {
}
