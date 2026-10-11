package com.bank.expenselimiter.exception;

public class LimitAlreadyExistsException extends RuntimeException {

    public LimitAlreadyExistsException(String message) {
        super(message);
    }
}
