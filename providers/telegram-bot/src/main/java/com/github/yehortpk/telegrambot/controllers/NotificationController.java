package com.github.yehortpk.telegrambot.controllers;

import com.github.yehortpk.telegrambot.models.VacancyNotificationDTO;
import com.github.yehortpk.telegrambot.services.NotifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@Component
@RequiredArgsConstructor
public class NotificationController {
    private final NotifierService notifierService;

    @KafkaListener(topics = {"#{environment['KAFKA_BOT_NOTIFIER_TOPIC']}"},
            containerFactory = "botNotifierContainerFactory")
    public void listenBotNotifierTopic(VacancyNotificationDTO vacancy) {
        notifierService.notifyUser(vacancy);
    }
}
