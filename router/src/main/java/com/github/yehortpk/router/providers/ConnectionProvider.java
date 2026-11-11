package com.github.yehortpk.router.providers;

import com.github.yehortpk.router.models.connection.Connection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the core needs from one messenger to link and manage its destinations.
 *
 * <p>The core owns the {@code connection} row, link sessions, rendering the provider's link
 * instructions, ownership and uniqueness across accounts. The provider owns what a
 * destination is: its details table, and how a reported destination is read and validated.
 * What the user has to do to link is data on the provider's catalog row, not code. Adding a
 * messenger means one implementation of this interface plus its migration, with no change to
 * the core.
 *
 * <p>Implementations run inside the core's transaction. They must not open their own
 * ({@code REQUIRES_NEW}) or swallow exceptions, so that a failure rolls back the
 * {@code connection} row together with the details row.
 *
 * @param <D> the provider's typed destination, e.g. a Telegram chat id
 */
public interface ConnectionProvider<D> {

    /** The id of this provider's catalog row, fixed by the migration that inserts it. */
    long getProviderId();

    /**
     * Reads a destination reported by this provider's own service.
     *
     * @throws com.github.yehortpk.router.exception.InvalidDestinationException if the fields
     *         do not describe a valid destination
     */
    D readDestination(Map<String, String> fields);

    /** The connection already holding this destination, whoever owns it. */
    Optional<Long> findConnectionId(D destination);

    /**
     * Stores the details row of a connection the core has just created.
     *
     * <p>Must flush, so that a uniqueness violation surfaces here, where the core turns it
     * into a conflict, rather than at commit.
     */
    void saveDestination(Connection connection, D destination);

    /**
     * Display forms of these connections' destinations, keyed by connection id.
     *
     * <p>A connection with no details row is left out rather than failing the whole lookup.
     */
    Map<Long, String> describeDestinations(Collection<Long> connectionIds);

    /** This provider's connections that have no details row, for the startup integrity check. */
    List<Long> findConnectionIdsWithoutDestination();

    /**
     * Releases anything held outside the database before the core deletes the connection,
     * e.g. revoking an OAuth token. The details row itself is removed by the database cascade.
     */
    default void releaseDestination(Connection connection) {
    }
}
