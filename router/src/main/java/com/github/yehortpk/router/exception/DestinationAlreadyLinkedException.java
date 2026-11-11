package com.github.yehortpk.router.exception;

public class DestinationAlreadyLinkedException extends RuntimeException {
    public DestinationAlreadyLinkedException() {
        super("This destination is already linked to another account");
    }
}
