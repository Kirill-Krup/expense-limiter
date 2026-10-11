package com.bank.expenselimiter.exception;

/** The provider could not give a rate (timeouts, 5xx, error payload, no data). Never leaks to API clients. */
public class ExchangeRateUnavailableException extends RuntimeException {

    public ExchangeRateUnavailableException(String message) {
        super(message);
    }

    public ExchangeRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
