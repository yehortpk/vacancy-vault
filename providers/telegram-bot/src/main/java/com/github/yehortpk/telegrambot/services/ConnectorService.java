package com.github.yehortpk.telegrambot.services;

import com.github.yehortpk.telegrambot.models.ConnectorStatusDTO;
import com.github.yehortpk.telegrambot.models.LinkedSignalDTO;
import com.github.yehortpk.telegrambot.models.UnlinkedSignalDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Calls the router's connector endpoints, which only accept the bot's shared service token.
 * Commands run on the thread that polls Telegram, so a hung router must not block it.
 *
 * <p>A chat is reported as the destination field {@code chatId}, which the router's Telegram
 * provider reads.
 */
@Service
public class ConnectorService {
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String CHAT_ID_FIELD = "chatId";

    private final RestTemplate restTemplate;

    @Value("${connector-url}")
    private String connectorURL;

    @Value("${SERVICE_CONNECTOR_TOKEN}")
    private String serviceToken;

    public ConnectorService(RestTemplateBuilder restTemplateBuilder) {
        restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpHeaders createServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SERVICE_TOKEN_HEADER, serviceToken);
        return headers;
    }

    private static Map<String, String> createDestination(long chatId) {
        return Map.of(CHAT_ID_FIELD, String.valueOf(chatId));
    }

    /**
     * Reports that the user sent their one-time link token from this chat.
     *
     * @param chatTitle the group's name, or null for a private chat
     */
    public void linkTelegramConnection(long chatId, String chatTitle, String token) {
        LinkedSignalDTO signal = new LinkedSignalDTO(token, chatTitle, createDestination(chatId));
        restTemplate.postForEntity(connectorURL + "/linked", new HttpEntity<>(signal, createServiceHeaders()), Void.class);
    }

    /** Reports that this chat should stop receiving notifications; a chat not linked is already done. */
    public void unlinkTelegramConnection(long chatId) {
        UnlinkedSignalDTO signal = new UnlinkedSignalDTO(createDestination(chatId));
        restTemplate.postForEntity(connectorURL + "/unlinked", new HttpEntity<>(signal, createServiceHeaders()), Void.class);
    }

    public ConnectorStatusDTO findTelegramStatus(long chatId) {
        return restTemplate.exchange(connectorURL + "/status?" + CHAT_ID_FIELD + "={chatId}", HttpMethod.GET,
                new HttpEntity<>(createServiceHeaders()), ConnectorStatusDTO.class, chatId).getBody();
    }
}
