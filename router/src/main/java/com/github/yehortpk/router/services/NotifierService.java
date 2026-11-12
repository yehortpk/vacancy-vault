package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.providers.ConnectionProvider;
import com.github.yehortpk.router.providers.ConnectionProviderRegistry;
import com.github.yehortpk.router.repositories.FilterRepository;
import com.github.yehortpk.router.utils.FilterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notifies the destinations whose filters match a newly stored vacancy.
 *
 * <p>Only active filters take part, i.e. those bound to at least one connection; drafts match
 * nothing. Each connection gets a vacancy at most once, however many of its filters match it,
 * while every other account's matches are still delivered.
 *
 * <p>The flow is filter → connections → providers: the matched connections are grouped by
 * provider, and each provider delivers to its own connections. A provider that fails, or has
 * no implementation, is logged and skipped, so it cannot stop delivery through the others.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotifierService {
    private final FilterRepository filterRepository;
    private final ConnectionProviderRegistry connectionProviderRegistry;

    /**
     * A stored expression that no longer parses (one edited by hand) is logged and treated as
     * not matching, so it cannot drop the vacancy for every other account.
     */
    private static boolean isMatching(Filter filter, VacancyDTO vacancy) {
        try {
            return new FilterParser(filter.getFilter())
                    .isVacancyApplicable(vacancy.getCompanyTitle(), vacancy.getTitle());
        } catch (RuntimeException ex) {
            log.warn("Skipped filter {}, its expression could not be parsed: {}", filter.getId(), ex.getMessage());
            return false;
        }
    }

    /**
     * Each matched connection with the first of its filters that matched, in filter order,
     * grouped by provider id.
     */
    private Map<Long, Map<Long, Filter>> findMatchedFiltersByProviderId(VacancyDTO vacancy) {
        Map<Long, Map<Long, Filter>> matchedFiltersByProviderId = new LinkedHashMap<>();
        for (Filter filter : filterRepository.findActive()) {
            if (isMatching(filter, vacancy)) {
                for (Connection connection : filter.getConnections()) {
                    matchedFiltersByProviderId
                            .computeIfAbsent(connection.getProvider().getId(), providerId -> new LinkedHashMap<>())
                            .putIfAbsent(connection.getId(), filter);
                }
            }
        }
        return matchedFiltersByProviderId;
    }

    public void notifyUsers(VacancyDTO vacancy) {
        findMatchedFiltersByProviderId(vacancy).forEach((providerId, matchedFiltersByConnectionId) -> {
            try {
                ConnectionProvider<?> provider = connectionProviderRegistry.findProvider(providerId);
                provider.notifyConnections(matchedFiltersByConnectionId, vacancy);
            } catch (RuntimeException ex) {
                log.error("Provider {} could not deliver {} to connections {}",
                        providerId, vacancy.getLink(), matchedFiltersByConnectionId.keySet(), ex);
            }
        });
    }
}
