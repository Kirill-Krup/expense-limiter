package com.bank.expenselimiter.domain;

import java.math.BigDecimal;

/**
 * Pure rule of the assignment: a transaction exceeds the limit when the month's spending
 * <em>including</em> this transaction is strictly greater than the limit. A remainder of exactly 0 is not an excess.
 */
public final class LimitEvaluator {

    private LimitEvaluator() {
    }

    public static boolean isExceeded(BigDecimal spentBefore, BigDecimal amountUsd, BigDecimal limitUsd) {
        return spentBefore.add(amountUsd).compareTo(limitUsd) > 0;
    }

    /** Remaining monthly limit after the transaction (negative when exceeded). */
    public static BigDecimal remainingAfter(BigDecimal spentBefore, BigDecimal amountUsd, BigDecimal limitUsd) {
        return limitUsd.subtract(spentBefore).subtract(amountUsd);
    }
}
