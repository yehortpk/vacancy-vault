package com.github.yehortpk.subscriberbot.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * The router's provider-independent "linked" signal: the one-time token from the account
 * page, the chat's name as a default title, and the destination fields the router's Telegram
 * provider reads ({@code chatId}).
 */
@Getter
@AllArgsConstructor
public class LinkedSignalDTO {
    private String token;
    private String title;
    private Map<String, String> destination;
}
