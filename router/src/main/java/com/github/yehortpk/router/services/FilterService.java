package com.github.yehortpk.router.services;

import com.github.yehortpk.router.exception.ConnectionNotFoundException;
import com.github.yehortpk.router.exception.FilterNotFoundException;
import com.github.yehortpk.router.exception.InvalidFilterExpressionException;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.filter.FilterDTO;
import com.github.yehortpk.router.models.filter.FilterRequestDTO;
import com.github.yehortpk.router.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyCompanyDTO;
import com.github.yehortpk.router.models.vacancy.VacancySearchCriteria;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.repositories.FilterRepository;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.utils.FilterParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * An account's own filters: listing, writing, and the vacancies a filter matches.
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
    /** As VacancyController orders the unfiltered listing; the id keeps pages stable. */
    private static final Comparator<Vacancy> NEWEST_FIRST = Comparator
            .comparing(Vacancy::getParsedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(Comparator.comparingInt(Vacancy::getVacancyID).reversed());

    private final FilterRepository filterRepository;
    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final VacancyService vacancyService;
    private final ModelMapper modelMapper;

    /**
     * @throws InvalidFilterExpressionException if the expression cannot be parsed
     */
    private static FilterParser parseExpression(String expression) {
        try {
            return new FilterParser(expression);
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
        parseExpression(request.filter().strip());

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

    /**
     * One page of the stored vacancies the caller's filter matches, newest first. Ordered in
     * memory since matching is not expressible in SQL.
     */
    public VacanciesPageDTO findVacanciesPage(long filterId, long userId, int pageId, int pageSize,
                                              VacancySearchCriteria criteria) {
        if (pageId < 0) {
            throw new IllegalArgumentException("page parameter could not be less then 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize parameter could not be less then 1");
        }
        FilterParser filterParser = parseExpression(findOwnedFilter(filterId, userId).getFilter());

        List<VacancyCompanyDTO> filteredVacancies = vacancyService.getAllVacancies().stream()
                .filter(criteria::isSatisfiedBy)
                .filter(vacancy -> filterParser.isVacancyApplicable(
                        vacancy.getCompany() == null ? null : vacancy.getCompany().getTitle(), vacancy.getTitle()))
                .sorted(NEWEST_FIRST)
                .map(vacancy -> modelMapper.map(vacancy, VacancyCompanyDTO.class))
                .toList();

        int totalSize = filteredVacancies.size();
        int totalPages = Math.max((int) Math.ceil((double) totalSize / pageSize), 1);

        if (pageId > totalPages) {
            throw new IllegalArgumentException("page parameter could not be greater then total pages count");
        }

        int start = Math.min(pageId * pageSize, totalSize);
        int end = Math.min(start + pageSize, totalSize);

        return VacanciesPageDTO.builder()
                .vacancies(filteredVacancies.subList(start, end))
                .currentPage(pageId)
                .totalVacancies(totalSize)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .build();
    }
}
