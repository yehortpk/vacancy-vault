package com.github.yehortpk.router.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String email) {
        super("An account for " + email + " already exists");
    }
}
