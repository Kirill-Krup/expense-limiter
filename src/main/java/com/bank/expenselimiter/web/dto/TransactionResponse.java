package com.bank.expenselimiter.web.dto;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response of the integration API: the stored transaction with its flag. */
public record TransactionResponse(
        Long id,
        String accountFrom,
        String accountTo,
        String currencyShortname,
        BigDecimal sum,
        ExpenseCategory expenseCategory,
        OffsetDateTime datetime,
        boolean limitExceeded,
        TransactionStatus status) {
}
