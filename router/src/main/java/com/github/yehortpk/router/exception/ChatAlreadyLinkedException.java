package com.github.yehortpk.router.exception;

public class ChatAlreadyLinkedException extends RuntimeException {
    public ChatAlreadyLinkedException(long chatId) {
        super("Chat " + chatId + " is already linked to an account");
    }
}
