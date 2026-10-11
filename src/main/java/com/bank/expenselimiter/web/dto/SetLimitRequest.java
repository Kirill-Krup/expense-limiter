package com.bank.expenselimiter.web.dto;

import com.bank.expenselimiter.domain.ExpenseCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** No date field on purpose: the limit date is always assigned by the server. */
public record SetLimitRequest(
        @NotNull @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String account,
        @NotNull ExpenseCategory expenseCategory,
        @NotNull @Positive @Digits(integer = 17, fraction = 2, message = "at most 2 digits after the decimal point") BigDecimal limitSum) {
}
