package com.bank.expenselimiter.web.dto;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.IncomingTransaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IncomingTransactionRequest(
        @NotNull @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String accountFrom,
        @NotNull @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String accountTo,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code in upper case") String currencyShortname,
        @NotNull @Positive @Digits(integer = 17, fraction = 2, message = "at most 2 digits after the decimal point") BigDecimal sum,
        @NotNull ExpenseCategory expenseCategory,
        @NotNull OffsetDateTime datetime) {

    public IncomingTransaction toCommand() {
        return new IncomingTransaction(accountFrom, accountTo, currencyShortname, sum, expenseCategory, datetime.toInstant());
    }
}
