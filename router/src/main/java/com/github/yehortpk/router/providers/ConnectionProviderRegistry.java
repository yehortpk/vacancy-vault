package com.github.yehortpk.router.providers;

import com.github.yehortpk.router.exception.ProviderNotAvailableException;
import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.repositories.ProviderRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Looks up the {@link ConnectionProvider} for a provider id.
 *
 * <p>Fails on startup if two implementations declare the same id, if an enabled
 * {@code provider} row has no implementation, or if an implementation has no row. Without
 * that, a catalog row and its code could drift apart and fail only when someone first tried
 * to link or list that provider.
 */
@Component
public class ConnectionProviderRegistry {
    private final Map<Long, ConnectionProvider<?>> providers;

    public ConnectionProviderRegistry(List<ConnectionProvider<?>> connectionProviders,
                                      ProviderRepository providerRepository) {
        Map<Long, ConnectionProvider<?>> providersById = new HashMap<>();
        for (ConnectionProvider<?> connectionProvider : connectionProviders) {
            if (providersById.putIfAbsent(connectionProvider.getProviderId(), connectionProvider) != null) {
                throw new IllegalStateException(
                        "More than one connection provider for provider id " + connectionProvider.getProviderId());
            }
        }

        List<Long> enabledWithoutImplementation = providerRepository.findEnabled().stream()
                .map(Provider::getId)
                .filter(id -> !providersById.containsKey(id))
                .toList();
        if (!enabledWithoutImplementation.isEmpty()) {
            throw new IllegalStateException(
                    "No connection provider for enabled provider ids " + enabledWithoutImplementation);
        }

        Set<Long> catalogIds = new HashSet<>();
        providerRepository.findAll().forEach(provider -> catalogIds.add(provider.getId()));
        List<Long> implementedWithoutRow = providersById.keySet().stream()
                .filter(id -> !catalogIds.contains(id))
                .sorted()
                .toList();
        if (!implementedWithoutRow.isEmpty()) {
            throw new IllegalStateException(
                    "No provider row for connection provider ids " + implementedWithoutRow);
        }

        this.providers = providersById;
    }

    /**
     * @throws ProviderNotAvailableException if no implementation declares this id
     */
    public ConnectionProvider<?> findProvider(long providerId) {
        ConnectionProvider<?> provider = providers.get(providerId);
        if (provider == null) {
            throw new ProviderNotAvailableException(providerId);
        }
        return provider;
    }

    public Collection<ConnectionProvider<?>> findAll() {
        return Collections.unmodifiableCollection(providers.values());
    }
}
