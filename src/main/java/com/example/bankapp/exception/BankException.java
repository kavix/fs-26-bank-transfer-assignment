package com.example.bankapp.exception;

/**
 * Base class for all banking errors.
 * Extends RuntimeException so Spring's @Transactional rolls back automatically.
 */
public class BankException extends RuntimeException {

    public BankException(String message) {
        super(message);
    }
}
