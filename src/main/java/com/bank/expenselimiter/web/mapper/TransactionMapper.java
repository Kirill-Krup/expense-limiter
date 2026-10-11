package com.bank.expenselimiter.web.mapper;

import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.web.dto.ExceededTransactionResponse;
import com.bank.expenselimiter.web.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class)
public interface TransactionMapper {

    @Mapping(target = "currencyShortname", source = "currency")
    @Mapping(target = "sum", source = "amount")
    @Mapping(target = "expenseCategory", source = "category")
    TransactionResponse toResponse(ExpenseTransaction transaction);

    @Mapping(target = "currencyShortname", source = "currency")
    @Mapping(target = "sum", source = "amount")
    @Mapping(target = "expenseCategory", source = "category")
    @Mapping(target = "limitSum", source = "appliedLimitSum")
    @Mapping(target = "limitDatetime", source = "appliedLimit.setAt")
    @Mapping(target = "limitCurrencyShortname", constant = "USD")
    ExceededTransactionResponse toExceeded(ExpenseTransaction transaction);
}
