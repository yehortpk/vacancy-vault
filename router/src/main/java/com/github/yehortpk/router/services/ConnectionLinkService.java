package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.ChatAlreadyLinkedException;
import com.github.yehortpk.router.exception.ConnectionNotFoundException;
import com.github.yehortpk.router.exception.InvalidApiKeyException;
import com.github.yehortpk.router.models.provider.ChannelProvider;
import com.github.yehortpk.router.models.provider.ConnectionDTO;
import com.github.yehortpk.router.models.connector.TelegramStatusDTO;
import com.github.yehortpk.router.models.provider.TelegramConnection;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.repositories.TelegramConnectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Links and unlinks a messenger destination to an account.
 *
 * <p>Everything a provider shares lives here — resolving the owner from a pasted API key,
 * enforcing one chat per account, and deleting a connection's filter bindings on unlink.
 * Only the request shape differs per messenger, which is why the connector controller is
 * namespaced per provider while this service is not.
 */
@Service
@RequiredArgsConstructor
public class ConnectionLinkService {
    private final TelegramConnectionRepository telegramConnectionRepository;
    private final ApiKeyService apiKeyService;
    private final ConnectionResolver connectionResolver;

    private AppUser findOwnerByApiKey(String apiKey) {
        return apiKeyService.findUserByApiKey(apiKey).orElseThrow(InvalidApiKeyException::new);
    }

    /**
     * Links a Telegram chat to the account the API key belongs to.
     *
     * <p>Re-linking the same chat to the same account is accepted and returns the existing
     * connection: a user running {@code /link} twice should not see an error. A chat already
     * held by a <em>different</em> account is refused, because the UNIQUE on
     * {@code chat_id} is what guarantees a chat has one owner.
     */
    @Transactional
    public ConnectionDTO linkTelegramConnection(long chatId, String apiKey) {
        AppUser owner = findOwnerByApiKey(apiKey);

        Optional<TelegramConnection> existing = telegramConnectionRepository.findByChatId(chatId);
        if (existing.isPresent()) {
            if (existing.get().getUser().getId() != owner.getId()) {
                throw new ChatAlreadyLinkedException(chatId);
            }
            return toDTO(existing.get());
        }

        TelegramConnection connection = new TelegramConnection();
        connection.setUser(owner);
        connection.setChatId(chatId);
        connection.setLinkedAt(LocalDateTime.now());

        return toDTO(telegramConnectionRepository.save(connection));
    }

    /**
     * Reports what the bot should tell a chat about itself.
     *
     * <p>The filter counts are zero until filters belong to users, which is the next change;
     * the chat's linked state is already meaningful and is what {@code /status} is for.
     */
    public TelegramStatusDTO findTelegramStatus(long chatId) {
        return telegramConnectionRepository.findByChatId(chatId)
                .map(connection -> new TelegramStatusDTO(true, connection.getUser().getEmail(), 0, 0))
                .orElseGet(TelegramStatusDTO::notLinked);
    }

    private ConnectionDTO toDTO(TelegramConnection connection) {
        return new ConnectionDTO(
                connection.getId(),
                ChannelProvider.TELEGRAM,
                connection.getTitle(),
                String.valueOf(connection.getChatId()),
                connection.getLinkedAt());
    }

    /** Unlinks a chat on the bot's behalf; a chat that is not linked is treated as done. */
    @Transactional
    public void unlinkTelegramConnection(long chatId) {
        telegramConnectionRepository.findByChatId(chatId)
                .ifPresent(connection -> deleteConnection(ChannelProvider.TELEGRAM, connection.getId()));
    }

    /** Unlinks a connection the authenticated owner names. */
    @Transactional
    public void deleteConnection(ChannelProvider provider, long connectionId, long userId) {
        connectionResolver.findTargetOwnedBy(provider, connectionId, userId)
                .orElseThrow(ConnectionNotFoundException::new);
        deleteConnection(provider, connectionId);
    }

    /**
     * Deletes the connection row.
     *
     * <p>TODO (filter bindings): {@code filter_provider.connection_id} will carry no foreign
     * key, so the database cannot cascade this. Once that table exists, its rows for this
     * connection must be deleted here, inside this same transaction — otherwise a filter is
     * left pointing at a destination that no longer exists, and the affected filters should
     * instead fall back to drafts. {@code filter_provider} does not exist yet, so there is
     * nothing to delete today; this is the one place that has to change when it lands.
     */
    private void deleteConnection(ChannelProvider provider, long connectionId) {
        switch (provider) {
            case TELEGRAM -> telegramConnectionRepository.deleteById(connectionId);
        }
    }

    @Transactional
    public ConnectionDTO renameConnection(ChannelProvider provider, long connectionId, long userId, String title) {
        connectionResolver.findTargetOwnedBy(provider, connectionId, userId)
                .orElseThrow(ConnectionNotFoundException::new);

        return switch (provider) {
            case TELEGRAM -> {
                TelegramConnection connection = telegramConnectionRepository.findById(connectionId)
                        .orElseThrow(ConnectionNotFoundException::new);
                connection.setTitle(title);
                yield toDTO(telegramConnectionRepository.save(connection));
            }
        };
    }
}
