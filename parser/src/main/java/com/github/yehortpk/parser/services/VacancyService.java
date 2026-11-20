package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.models.ParsedVacancy;
import com.github.yehortpk.parser.repositories.ParsedVacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The parser's memory of the vacancies it has already published, see {@link ParsedVacancy}.
 */
@Service
@RequiredArgsConstructor
public class VacancyService {
    private final ParsedVacancyRepository parsedVacancyRepository;

    /**
     * Returns the vacancies of a company published by earlier runs and not dropped yet
     * @param companyId id of company
     * @return list of persisted vacancies
     */
    public List<ParsedVacancy> getPersistedVacancies(int companyId) {
        return parsedVacancyRepository.findByCompanyId(companyId);
    }

    /** Those of the given links that are already published, whichever company they belong to. */
    public Set<String> findPublishedLinks(Collection<String> links) {
        return links.isEmpty() ? Set.of() : parsedVacancyRepository.findPublishedLinks(links);
    }

    /** Remembers a vacancy as published, so later runs no longer see it as new. */
    public void addPublishedVacancy(String link, int companyId, LocalDateTime parsedAt) {
        parsedVacancyRepository.save(ParsedVacancy.builder()
                .link(link)
                .companyId(companyId)
                .parsedAt(parsedAt)
                .build());
    }

    /** Starts the grace period of vacancies that are gone from their site. */
    @Transactional
    public void markOutdated(Collection<String> links, LocalDateTime deleteAt) {
        if (!links.isEmpty()) {
            parsedVacancyRepository.updateDeleteAt(links, deleteAt);
        }
    }

    /** Lifts the deletion marker of vacancies that showed up on their site again. */
    @Transactional
    public void restoreOutdated(Collection<String> links) {
        if (!links.isEmpty()) {
            parsedVacancyRepository.updateDeleteAt(links, null);
        }
    }

    @Transactional
    public void deleteVacancies(Collection<String> links) {
        if (!links.isEmpty()) {
            parsedVacancyRepository.deleteByLinks(links);
        }
    }
}
