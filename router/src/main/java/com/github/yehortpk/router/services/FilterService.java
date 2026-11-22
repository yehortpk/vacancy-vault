package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.ConnectionNotFoundException;
import com.github.yehortpk.router.exception.FilterNotFoundException;
import com.github.yehortpk.router.exception.InvalidFilterExpressionException;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.filter.FilterDTO;
import com.github.yehortpk.router.models.filter.FilterRequestDTO;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.repositories.FilterRepository;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.utils.FilterParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * An account's own filters: listing and writing.
 *
 * <p>Every filter id and every connection id a caller names is looked up scoped to the
 * caller, and one that belongs to someone else answers 404. For bindings this check is the
 * whole protection: the foreign key on {@code filter_connection} proves that a connection
 * exists, not that it is the caller's, and without it a filter could be bound to a stranger's
 * chat.
 */
@Service
@RequiredArgsConstructor
public class FilterService {
    private final FilterRepository filterRepository;
    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    /**
     * @throws InvalidFilterExpressionException if the expression cannot be parsed
     */
    private static void validateExpression(String expression) {
        try {
            new FilterParser(expression);
        } catch (RuntimeException ex) {
            throw new InvalidFilterExpressionException(expression);
        }
    }

    private static FilterDTO toDTO(Filter filter) {
        List<Long> connectionIds = filter.getConnections().stream()
                .map(Connection::getId)
                .sorted()
                .toList();
        return new FilterDTO(filter.getId(), filter.getTitle(), filter.getFilter(), connectionIds);
    }

    private Filter findOwnedFilter(long filterId, long userId) {
        return filterRepository.findByIdAndUserId(filterId, userId)
                .orElseThrow(FilterNotFoundException::new);
    }

    /**
     * @throws ConnectionNotFoundException if any id is not one of the caller's connections
     */
    private Set<Connection> findOwnedConnections(List<Long> connectionIds, long userId) {
        Set<Connection> connections = new LinkedHashSet<>();
        if (connectionIds == null) {
            return connections;
        }
        for (Long connectionId : new LinkedHashSet<>(connectionIds)) {
            connections.add(connectionRepository.findByIdAndUserId(connectionId, userId)
                    .orElseThrow(ConnectionNotFoundException::new));
        }
        return connections;
    }

    private void applyRequest(Filter filter, FilterRequestDTO request, long userId) {
        validateExpression(request.filter().strip());

        filter.setTitle(request.title().strip());
        filter.setFilter(request.filter().strip());
        filter.setConnections(findOwnedConnections(request.connectionIds(), userId));
    }

    public List<FilterDTO> findFilters(long userId) {
        return filterRepository.findByUserId(userId).stream()
                .map(FilterService::toDTO)
                .toList();
    }

    public FilterDTO findFilter(long filterId, long userId) {
        return toDTO(findOwnedFilter(filterId, userId));
    }

    @Transactional
    public FilterDTO createFilter(long userId, FilterRequestDTO request) {
        Filter filter = new Filter();
        filter.setUser(userRepository.findById(userId).orElseThrow(
                () -> new IllegalStateException("Authenticated account " + userId + " no longer exists")));
        applyRequest(filter, request, userId);
        return toDTO(filterRepository.save(filter));
    }

    /** Replaces the title, the expression and the bindings as a whole. */
    @Transactional
    public FilterDTO updateFilter(long filterId, long userId, FilterRequestDTO request) {
        Filter filter = findOwnedFilter(filterId, userId);
        applyRequest(filter, request, userId);
        return toDTO(filterRepository.save(filter));
    }

    @Transactional
    public void deleteFilter(long filterId, long userId) {
        filterRepository.delete(findOwnedFilter(filterId, userId));
    }
}
