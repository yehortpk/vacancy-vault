package com.github.yehortpk.router.exception;

/**
 * Raised when a filter does not exist <em>or</em> belongs to another account; the two are
 * deliberately indistinguishable, so filter ids cannot be enumerated.
 */
public class FilterNotFoundException extends RuntimeException {
    public FilterNotFoundException() {
        super("Filter not found");
    }
}
