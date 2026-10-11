package com.bank.expenselimiter.exception;

public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String code) {
        super("Unknown ISO 4217 currency code: " + code);
    }
}
