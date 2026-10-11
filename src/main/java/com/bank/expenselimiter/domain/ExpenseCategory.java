package com.bank.expenselimiter.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExpenseCategory {
    @JsonProperty("product") PRODUCT,
    @JsonProperty("service") SERVICE
}
