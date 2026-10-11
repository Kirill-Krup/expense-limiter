package com.bank.expenselimiter.client;

import com.bank.expenselimiter.exception.ExchangeRateUnavailableException;

import java.time.LocalDate;

/**
 * Port to an external source of daily close rates. To add another source (alphavantage, openexchangerates, ...)
 * implement this interface and register the bean with {@code @ConditionalOnProperty("app.rate-provider")}.
 */
public interface ExchangeRateProvider {

    /** Short stable name stored with every cached rate, e.g. {@code twelvedata}. */
    String name();

    /**
     * Close rate of {@code currency/USD} for the day, or the last available close before it.
     *
     * @throws ExchangeRateUnavailableException when no rate could be obtained
     */
    RateQuote fetchDailyClose(String currency, LocalDate date);
}
