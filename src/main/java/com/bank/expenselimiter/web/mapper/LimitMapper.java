package com.bank.expenselimiter.web.mapper;

import com.bank.expenselimiter.domain.SpendingLimit;
import com.bank.expenselimiter.web.dto.LimitResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class)
public interface LimitMapper {

    @Mapping(target = "expenseCategory", source = "category")
    @Mapping(target = "limitDatetime", source = "setAt")
    @Mapping(target = "limitCurrencyShortname", constant = "USD")
    LimitResponse toResponse(SpendingLimit limit);
}
