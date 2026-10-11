package com.bank.expenselimiter.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LimitEvaluatorTest {

    @ParameterizedTest(name = "spent={0} + amount={1} vs limit={2} -> exceeded={3}")
    @CsvSource({
            "0,      500,  1000, false",
            "500,    500,  1000, false",   // remainder exactly 0 is NOT an excess
            "500,    500.01, 1000, true",  // one cent over
            "500,    600,  1000, true",
            "1100,   100,  2000, false",
            "1900,   100,  2000, false",
            "2000,   100,  2000, true",
            "600,    100,  400,  true"     // limit lowered below what is already spent
    })
    void exceededOnlyWhenTotalIsStrictlyGreaterThanLimit(String spent, String amount, String limit, boolean expected) {
        assertThat(LimitEvaluator.isExceeded(new BigDecimal(spent), new BigDecimal(amount), new BigDecimal(limit)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, 500, 1000, 500", "500, 600, 1000, -100", "1900, 100, 2000, 0", "600, 100, 400, -300"})
    void remainingMatchesAssignmentTable(String spent, String amount, String limit, String remaining) {
        assertThat(LimitEvaluator.remainingAfter(new BigDecimal(spent), new BigDecimal(amount), new BigDecimal(limit)))
                .isEqualByComparingTo(remaining);
    }
}
