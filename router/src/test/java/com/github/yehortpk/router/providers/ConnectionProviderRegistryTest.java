package com.github.yehortpk.router.providers;

import com.github.yehortpk.router.exception.ProviderNotAvailableException;
import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.repositories.ProviderRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionProviderRegistryTest {
    private static final long TELEGRAM_ID = 1;

    private ConnectionProvider<?> createProvider(long providerId) {
        ConnectionProvider<?> provider = mock(ConnectionProvider.class);
        when(provider.getProviderId()).thenReturn(providerId);
        return provider;
    }

    private Provider createRow(long id, boolean enabled) {
        return new Provider(id, "Provider " + id, "Use {token}", enabled);
    }

    private ProviderRepository createCatalog(Provider... rows) {
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        when(providerRepository.findAll()).thenReturn(List.of(rows));
        when(providerRepository.findEnabled()).thenReturn(Arrays.stream(rows).filter(Provider::isEnabled).toList());
        return providerRepository;
    }

    @Test
    void Constructor_EveryProviderImplemented_ShouldFindEachById() {
        ConnectionProvider<?> telegram = createProvider(TELEGRAM_ID);

        ConnectionProviderRegistry registry = new ConnectionProviderRegistry(
                List.of(telegram), createCatalog(createRow(TELEGRAM_ID, true)));

        assertThat(registry.findProvider(TELEGRAM_ID)).isSameAs(telegram);
        assertThat(registry.findAll()).containsExactly(telegram);
    }

    @Test
    void Constructor_EnabledRowWithoutImplementation_ShouldFailOnStartup() {
        // Otherwise the provider would be offered in the panel and fail on its first link
        ProviderRepository catalog = createCatalog(createRow(TELEGRAM_ID, true));

        assertThatThrownBy(() -> new ConnectionProviderRegistry(List.of(), catalog))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No connection provider")
                .hasMessageContaining(String.valueOf(TELEGRAM_ID));
    }

    @Test
    void Constructor_DisabledRowWithoutImplementation_ShouldStart() {
        // A disabled row is never offered, so it may wait for its implementation
        ConnectionProviderRegistry registry = new ConnectionProviderRegistry(
                List.of(), createCatalog(createRow(TELEGRAM_ID, false)));

        assertThat(registry.findAll()).isEmpty();
    }

    @Test
    void Constructor_ImplementationWithoutRow_ShouldFailOnStartup() {
        // Its connections would reference a provider id the foreign keys refuse
        List<ConnectionProvider<?>> providers = List.of(createProvider(TELEGRAM_ID), createProvider(42));
        ProviderRepository catalog = createCatalog(createRow(TELEGRAM_ID, true));

        assertThatThrownBy(() -> new ConnectionProviderRegistry(providers, catalog))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No provider row")
                .hasMessageContaining("42");
    }

    @Test
    void Constructor_TwoImplementationsOfOneProvider_ShouldFailOnStartup() {
        List<ConnectionProvider<?>> providers = List.of(createProvider(TELEGRAM_ID), createProvider(TELEGRAM_ID));
        ProviderRepository catalog = createCatalog(createRow(TELEGRAM_ID, true));

        assertThatThrownBy(() -> new ConnectionProviderRegistry(providers, catalog))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one");
    }

    @Test
    void FindProvider_UnknownId_ShouldThrowProviderNotAvailable() {
        ConnectionProviderRegistry registry = new ConnectionProviderRegistry(
                List.of(createProvider(TELEGRAM_ID)), createCatalog(createRow(TELEGRAM_ID, true)));

        assertThatThrownBy(() -> registry.findProvider(999))
                .isInstanceOf(ProviderNotAvailableException.class);
    }
}
