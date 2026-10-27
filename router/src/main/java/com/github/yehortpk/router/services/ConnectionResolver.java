package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.provider.ChannelProvider;
import com.github.yehortpk.router.models.provider.ConnectionDTO;
import com.github.yehortpk.router.models.provider.ConnectionTarget;
import com.github.yehortpk.router.models.provider.TelegramConnection;
import com.github.yehortpk.router.repositories.TelegramConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a {@code (provider, connectionId)} pair into a real destination.
 *
 * <p>Connection ids live in separate per-provider tables, so SQL cannot foreign-key into
 * "whichever table the provider column names". That dispatch happens here instead, in one
 * place: adding a messenger means adding a branch and its repository, rather than another
 * switch somewhere in the notifier or in filter validation.
 *
 * <p>Note that a connection id is only unique *within* one provider's table. Any key built
 * from one — a dedup key, a map key — must include the provider too.
 */
@Service
@RequiredArgsConstructor
public class ConnectionResolver {
    private final TelegramConnectionRepository telegramConnectionRepository;

    private ConnectionDTO toDTO(TelegramConnection connection) {
        return new ConnectionDTO(
                connection.getId(),
                ChannelProvider.TELEGRAM,
                connection.getTitle(),
                String.valueOf(connection.getChatId()),
                connection.getLinkedAt());
    }

    /**
     * Loads a connection scoped to its owner.
     *
     * <p>This is the security-critical path. Because {@code filter_provider.connection_id}
     * has no foreign key, the database will not stop a user binding a filter to someone
     * else's chat, which would deliver their notifications to a stranger. Every binding
     * write resolves through here first, and an id belonging to another account comes back
     * empty — reported as a 404, so ids stay unenumerable.
     */
    public Optional<ConnectionTarget> findTargetOwnedBy(ChannelProvider provider, long connectionId, long userId) {
        return switch (provider) {
            case TELEGRAM -> telegramConnectionRepository.findByIdAndUserId(connectionId, userId)
                    .map(ConnectionTarget::ofTelegram);
        };
    }

    /** Resolves a binding's destination without an ownership check, for the notifier. */
    public Optional<ConnectionTarget> findTarget(ChannelProvider provider, long connectionId) {
        return switch (provider) {
            case TELEGRAM -> telegramConnectionRepository.findById(connectionId)
                    .map(ConnectionTarget::ofTelegram);
        };
    }

    /**
     * Resolves many bindings at once, one query per provider.
     *
     * <p>The notifier fans one vacancy out across every active filter, so resolving each
     * binding on its own would be a query per filter per vacancy.
     */
    public Map<ChannelProvider, Map<Long, ConnectionTarget>> findTargets(
            Map<ChannelProvider, List<Long>> connectionIdsByProvider) {

        Map<ChannelProvider, Map<Long, ConnectionTarget>> targets = new java.util.EnumMap<>(ChannelProvider.class);

        connectionIdsByProvider.forEach((provider, connectionIds) -> {
            List<ConnectionTarget> resolved = switch (provider) {
                case TELEGRAM -> telegramConnectionRepository.findByIds(connectionIds).stream()
                        .map(ConnectionTarget::ofTelegram)
                        .toList();
            };
            targets.put(provider, resolved.stream()
                    .collect(Collectors.toMap(ConnectionTarget::connectionId, Function.identity())));
        });

        return targets;
    }

    /** Every connection the user has linked, across all providers. */
    public List<ConnectionDTO> findConnections(long userId) {
        List<ConnectionDTO> connections = new ArrayList<>();

        for (ChannelProvider provider : ChannelProvider.values()) {
            switch (provider) {
                case TELEGRAM -> telegramConnectionRepository.findByUserId(userId).stream()
                        .map(this::toDTO)
                        .forEach(connections::add);
            }
        }

        return connections;
    }
}
