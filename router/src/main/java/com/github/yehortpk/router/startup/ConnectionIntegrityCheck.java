package com.github.yehortpk.router.startup;

import com.github.yehortpk.router.providers.ConnectionProvider;
import com.github.yehortpk.router.providers.ConnectionProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Logs connections that have no details row in their provider's table.
 *
 * <p>This is the one rule the schema does not enforce: the core creates both rows in one
 * transaction, but a row removed by hand, or a provider bug, can leave a connection with
 * nowhere to deliver. Such a connection shows without an address in the panel; this check
 * only reports it, deleting is left to a person.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionIntegrityCheck {
    private final ConnectionProviderRegistry connectionProviderRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionsWithoutDestination() {
        for (ConnectionProvider<?> provider : connectionProviderRegistry.findAll()) {
            List<Long> connectionIds = provider.findConnectionIdsWithoutDestination();
            if (!connectionIds.isEmpty()) {
                log.warn("{} connections without a details row of provider {}: {}",
                        connectionIds.size(), provider.getProviderId(), connectionIds);
            }
        }
    }
}
