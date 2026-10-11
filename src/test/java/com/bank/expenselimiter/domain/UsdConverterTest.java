package com.bank.expenselimiter.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UsdConverterTest {

    @Test
    void convertsAndRoundsHalfUpToTwoDecimals() {
        assertThat(UsdConverter.toUsd(new BigDecimal("10000.45"), new BigDecimal("0.0022310000")))
                .isEqualTo(new BigDecimal("22.31"));
        assertThat(UsdConverter.toUsd(new BigDecimal("1.00"), new BigDecimal("0.0050000000")))
                .isEqualTo(new BigDecimal("0.01")); // 0.005 -> HALF_UP
    }

    @Test
    void resultAlwaysHasScaleTwo() {
        assertThat(UsdConverter.toUsd(new BigDecimal("50000.00"), new BigDecimal("0.01")).scale()).isEqualTo(2);
    }
}
