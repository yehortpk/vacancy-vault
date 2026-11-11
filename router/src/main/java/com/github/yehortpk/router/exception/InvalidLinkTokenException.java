package com.github.yehortpk.router.exception;

/**
 * Raised when a reported link token is unknown, already used, expired, or was issued for
 * another provider; the cases are deliberately indistinguishable.
 */
public class InvalidLinkTokenException extends RuntimeException {
    public InvalidLinkTokenException() {
        super("Link code is not valid or has expired");
    }
}
