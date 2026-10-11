package com.bank.expenselimiter.web.dto;

import com.bank.expenselimiter.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LimitResponse(
        Long id,
        String account,
        ExpenseCategory expenseCategory,
        BigDecimal limitSum,
        OffsetDateTime limitDatetime,
        String limitCurrencyShortname) {
}
