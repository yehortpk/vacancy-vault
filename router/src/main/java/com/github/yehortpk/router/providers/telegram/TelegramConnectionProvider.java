package com.github.yehortpk.router.providers.telegram;

import com.github.yehortpk.router.exception.InvalidDestinationException;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.providers.ConnectionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Telegram as a provider: the bot reports the chat a {@code /link} command came from, and that
 * chat receives the notifications, which go to the bot over the {@code bot-notifier} topic.
 *
 * <p>Its catalog row and {@code telegram_connection} come from
 * {@code V10__create_telegram_provider.sql}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramConnectionProvider implements ConnectionProvider<TelegramDestination> {
    /** The Telegram row's fixed id in the provider catalog; the bot's connector url uses it too. */
    public static final long PROVIDER_ID = 1;

    /** The destination field the bot reports, as a decimal string. */
    public static final String CHAT_ID_FIELD = "chatId";

    private final TelegramConnectionRepository telegramConnectionRepository;
    private final KafkaTemplate<String, TelegramNotificationDTO> kafkaTemplate;

    private static TelegramNotificationDTO createNotification(VacancyDTO vacancy, Filter filter, long chatId) {
        return TelegramNotificationDTO.builder()
                .vacancyTitle(vacancy.getTitle())
                .filter(filter.getFilter())
                .filterId(filter.getId())
                .minSalary(vacancy.getMinSalary())
                .maxSalary(vacancy.getMaxSalary())
                .salaryCurrency(vacancy.getSalaryCurrency())
                .salaryPeriod(vacancy.getSalaryPeriod())
                .companyTitle(vacancy.getCompanyTitle())
                .chatId(chatId)
                .link(vacancy.getLink())
                .isRemote(vacancy.isRemote())
                .build();
    }

    /**
     * The chat ids of these connections, keyed by connection id. Telegram connections without
     * a details row are left out.
     */
    private Map<Long, Long> findChatIds(Collection<Long> connectionIds) {
        if (connectionIds.isEmpty()) {
            return Map.of();
        }
        return telegramConnectionRepository.findByConnectionIds(connectionIds).stream()
                .collect(Collectors.toMap(TelegramConnection::getConnectionId, TelegramConnection::getChatId));
    }

    @Override
    public long getProviderId() {
        return PROVIDER_ID;
    }

    /**
     * Group chat ids are negative, so only zero and non-numbers are refused.
     */
    @Override
    public TelegramDestination readDestination(Map<String, String> fields) {
        String chatId = fields.get(CHAT_ID_FIELD);
        if (chatId == null || chatId.isBlank()) {
            throw new InvalidDestinationException(CHAT_ID_FIELD + " is required");
        }

        long parsedChatId;
        try {
            parsedChatId = Long.parseLong(chatId.strip());
        } catch (NumberFormatException ex) {
            throw new InvalidDestinationException(CHAT_ID_FIELD + " must be a number");
        }
        if (parsedChatId == 0) {
            throw new InvalidDestinationException(CHAT_ID_FIELD + " must not be zero");
        }
        return new TelegramDestination(parsedChatId);
    }

    @Override
    public Optional<Long> findConnectionId(TelegramDestination destination) {
        return telegramConnectionRepository.findConnectionIdByChatId(destination.chatId());
    }

    @Override
    public void saveDestination(Connection connection, TelegramDestination destination) {
        TelegramConnection telegramConnection = new TelegramConnection();
        telegramConnection.setConnection(connection);
        telegramConnection.setChatId(destination.chatId());
        telegramConnectionRepository.saveAndFlush(telegramConnection);
    }

    @Override
    public Map<Long, String> describeDestinations(Collection<Long> connectionIds) {
        return findChatIds(connectionIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

    @Override
    public List<Long> findConnectionIdsWithoutDestination() {
        return telegramConnectionRepository.findConnectionIdsWithoutDetails(PROVIDER_ID);
    }

    /** One {@code bot-notifier} message per chat, all chat ids read in one query. */
    @Override
    public void notifyConnections(Map<Long, Filter> matchedFiltersByConnectionId, VacancyDTO vacancy) {
        Map<Long, Long> chatIds = findChatIds(matchedFiltersByConnectionId.keySet());
        matchedFiltersByConnectionId.forEach((connectionId, filter) -> {
            Long chatId = chatIds.get(connectionId);
            if (chatId == null) {
                log.warn("Telegram connection {} has no chat to deliver {} to", connectionId, vacancy.getLink());
                return;
            }
            kafkaTemplate.sendDefault(createNotification(vacancy, filter, chatId));
        });
    }
}
