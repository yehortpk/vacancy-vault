package com.github.yehortpk.telegrambot.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Internal model that encapsulates a user's {@link Update} request together with the chat it
 * came from. The bot keeps no state of its own: whether a chat is linked, and to which account,
 * is the router's to answer.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserRequestDTO {
    private long chatId;
    private Update update;
}
