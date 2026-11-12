package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.ConnectionNotFoundException;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.connection.ConnectionDTO;
import com.github.yehortpk.router.providers.ConnectionProviderRegistry;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.utils.ServerClock;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lists, renames and unlinks an account's connections, for every provider alike.
 *
 * <p>Works on the {@code connection} row only. The destination's display form is the one
 * thing asked of the provider, one batch per provider.
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {
    private final ConnectionRepository connectionRepository;
    private final ConnectionProviderRegistry connectionProviderRegistry;

    private Map<Long, String> describeDestinations(Collection<Connection> connections) {
        Map<Long, List<Long>> connectionIdsByProviderId = connections.stream()
                .collect(Collectors.groupingBy(
                        connection -> connection.getProvider().getId(),
                        Collectors.mapping(Connection::getId, Collectors.toList())));

        Map<Long, String> addresses = new HashMap<>();
        connectionIdsByProviderId.forEach((providerId, connectionIds) -> addresses.putAll(
                connectionProviderRegistry.findProvider(providerId).describeDestinations(connectionIds)));
        return addresses;
    }

    private ConnectionDTO toDTO(Connection connection, String address) {
        return new ConnectionDTO(
                connection.getId(),
                connection.getProvider().getId(),
                connection.getTitle(),
                address,
                ServerClock.toOffsetDateTime(connection.getLinkedAt()));
    }

    private Connection findOwnedConnection(long connectionId, long userId) {
        return connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(ConnectionNotFoundException::new);
    }

    public ConnectionDTO toDTO(Connection connection) {
        return toDTO(connection, describeDestinations(List.of(connection)).get(connection.getId()));
    }

    /** Every connection the user has linked, across all providers. */
    public List<ConnectionDTO> findConnections(long userId) {
        List<Connection> connections = connectionRepository.findByUserId(userId);
        Map<Long, String> addresses = describeDestinations(connections);

        return connections.stream()
                .map(connection -> toDTO(connection, addresses.get(connection.getId())))
                .toList();
    }

    @Transactional
    public ConnectionDTO renameConnection(long connectionId, long userId, String title) {
        Connection connection = findOwnedConnection(connectionId, userId);
        connection.setTitle(title.strip());
        return toDTO(connectionRepository.save(connection));
    }

    /** Unlinks a connection the authenticated owner names. */
    @Transactional
    public void deleteOwnedConnection(long connectionId, long userId) {
        deleteConnection(findOwnedConnection(connectionId, userId));
    }

    /**
     * Unlinks a connection.
     *
     * <p>Always deletes the {@code connection} row, never a provider's details row: the
     * database cascades from here to the details row and to the filter bindings, so a filter
     * bound only to this connection stays as a draft.
     */
    @Transactional
    public void deleteConnection(Connection connection) {
        connectionProviderRegistry.findProvider(connection.getProvider().getId()).releaseDestination(connection);
        connectionRepository.delete(connection);
    }
}
