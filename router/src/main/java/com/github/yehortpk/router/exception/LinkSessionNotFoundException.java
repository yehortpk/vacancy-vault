package com.github.yehortpk.router.exception;

/** Raised when a link session does not exist or belongs to another account. */
public class LinkSessionNotFoundException extends RuntimeException {
    public LinkSessionNotFoundException() {
        super("Link session not found");
    }
}
