package com.github.yehortpk.telegramprovider.exceptions;

/**
 * Exception that can be thrown if user is empty or null
 */
public class EmptyUserException extends RuntimeException {
    public EmptyUserException(String message) {
        super(message);
    }
}
