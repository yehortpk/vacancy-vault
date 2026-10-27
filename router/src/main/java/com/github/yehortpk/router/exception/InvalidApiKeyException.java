package com.github.yehortpk.router.exception;

public class InvalidApiKeyException extends RuntimeException {
    public InvalidApiKeyException() {
        super("API key is not valid");
    }
}
