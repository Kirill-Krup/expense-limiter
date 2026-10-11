package com.bank.expenselimiter.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UsdConverter {

    public static final int MONEY_SCALE = 2;

    private UsdConverter() {
    }

    /** @param rate USD per one unit of the transaction currency (e.g. KZT/USD close) */
    public static BigDecimal toUsd(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
