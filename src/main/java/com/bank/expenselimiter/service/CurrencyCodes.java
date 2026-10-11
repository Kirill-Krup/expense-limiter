package com.bank.expenselimiter.service;

import com.bank.expenselimiter.exception.UnsupportedCurrencyException;

import java.util.Currency;

/** Helper for ISO 4217 codes. */
public final class CurrencyCodes {

    public static final String USD = "USD";

    private CurrencyCodes() {
    }

    public static boolean isUsd(String code) {
        return USD.equals(code);
    }

    public static void requireKnown(String code) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedCurrencyException(code);
        }
    }
}
