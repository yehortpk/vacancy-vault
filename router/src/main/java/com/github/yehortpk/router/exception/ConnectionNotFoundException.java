package com.github.yehortpk.router.exception;

/**
 * Raised when a connection does not exist <em>or</em> belongs to another account; the two
 * are deliberately indistinguishable, so connection ids cannot be enumerated.
 */
public class ConnectionNotFoundException extends RuntimeException {
    public ConnectionNotFoundException() {
        super("Connection not found");
    }
}
