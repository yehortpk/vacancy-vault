package com.github.yehortpk.telegrambot.exceptions;

/**
 * Exception that can be thrown if message type from client hasn't appropriate handler
 */
public class MissedHandlerException extends RuntimeException {
    public MissedHandlerException(String message) {
        super(message);
    }
}
