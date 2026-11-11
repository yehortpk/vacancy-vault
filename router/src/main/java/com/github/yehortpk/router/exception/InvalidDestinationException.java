package com.github.yehortpk.router.exception;

/**
 * Raised by a provider when the fields it was given do not describe a destination.
 *
 * <p>An {@link IllegalArgumentException}, so it answers 400 like other malformed input.
 */
public class InvalidDestinationException extends IllegalArgumentException {
    public InvalidDestinationException(String message) {
        super(message);
    }
}
