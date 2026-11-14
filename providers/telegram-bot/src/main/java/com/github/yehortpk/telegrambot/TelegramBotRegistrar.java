package com.github.yehortpk.telegrambot;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramBotRegistrar implements ApplicationRunner {
    private final TelegramNotifierBot telegramNotifierBot;
    // Held so the polling thread can be stopped with the context; otherwise a devtools restart leaves it running
    // next to the new session and Telegram rejects both with 409 Conflict
    private BotSession botSession;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            log.info("Registering bot...");
            log.info(telegramNotifierBot.toString());
            botSession = telegramBotsApi.registerBot(telegramNotifierBot);
        } catch (TelegramApiRequestException e) {
            log.error("Failed to register bot(check internet connection / bot token or make sure only one instance " +
                    "of bot is running).", e);
        }
        log.info("Telegram bot is ready to accept updates from user......");
    }

    @PreDestroy
    public void stopBotSession() {
        if (botSession != null && botSession.isRunning()) {
            log.info("Stopping bot session...");
            botSession.stop();
        }
    }
}
