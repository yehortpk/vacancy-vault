package com.github.yehortpk.router.providers.stub;

import com.github.yehortpk.router.exception.InvalidDestinationException;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.providers.ConnectionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A provider that exists only in the test sources, so linking can be tested end to end
 * without a real messenger. Its catalog row and details table come from the test-only
 * {@code R__create_stub_provider.sql}.
 */
@Component
@RequiredArgsConstructor
public class StubConnectionProvider implements ConnectionProvider<StubDestination> {
    /** The stub row's fixed id in the provider catalog. */
    public static final long PROVIDER_ID = 99;

    /** The destination field a stub service reports. */
    public static final String ADDRESS_FIELD = "address";

    private final StubConnectionRepository stubConnectionRepository;

    @Override
    public long getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public StubDestination readDestination(Map<String, String> fields) {
        String address = fields.get(ADDRESS_FIELD);
        if (address == null || address.isBlank()) {
            throw new InvalidDestinationException(ADDRESS_FIELD + " is required");
        }
        return new StubDestination(address.strip());
    }

    @Override
    public Optional<Long> findConnectionId(StubDestination destination) {
        return stubConnectionRepository.findConnectionIdByAddress(destination.address());
    }

    @Override
    public void saveDestination(Connection connection, StubDestination destination) {
        StubConnection stubConnection = new StubConnection();
        stubConnection.setConnection(connection);
        stubConnection.setAddress(destination.address());
        stubConnectionRepository.saveAndFlush(stubConnection);
    }

    @Override
    public Map<Long, String> describeDestinations(Collection<Long> connectionIds) {
        return stubConnectionRepository.findByConnectionIds(connectionIds).stream()
                .collect(Collectors.toMap(StubConnection::getConnectionId, StubConnection::getAddress));
    }

    @Override
    public List<Long> findConnectionIdsWithoutDestination() {
        return stubConnectionRepository.findConnectionIdsWithoutDetails(PROVIDER_ID);
    }

    /** Delivers nothing; tests that care spy on this method. */
    @Override
    public void notifyConnections(Map<Long, Filter> matchedFiltersByConnectionId, VacancyDTO vacancy) {
    }
}
