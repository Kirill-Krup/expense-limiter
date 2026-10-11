package com.bank.expenselimiter.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param rate       USD per one unit of the requested currency
 * @param sourceDate the trading day the quote actually belongs to (earlier than requested on weekends/holidays)
 */
public record RateQuote(BigDecimal rate, LocalDate sourceDate) {
}
