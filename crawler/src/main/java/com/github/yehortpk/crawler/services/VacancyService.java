package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.models.CrawledVacancy;
import com.github.yehortpk.crawler.repositories.CrawledVacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The crawler's memory of the vacancies it has already published, see {@link CrawledVacancy}.
 */
@Service
@RequiredArgsConstructor
public class VacancyService {
    private final CrawledVacancyRepository crawledVacancyRepository;

    /**
     * Returns the vacancies of a company published by earlier runs and not dropped yet
     * @param companyId id of company
     * @return list of persisted vacancies
     */
    public List<CrawledVacancy> getPersistedVacancies(int companyId) {
        return crawledVacancyRepository.findByCompanyId(companyId);
    }

    /** Those of the given links that are already published, whichever company they belong to. */
    public Set<String> findPublishedLinks(Collection<String> links) {
        return links.isEmpty() ? Set.of() : crawledVacancyRepository.findPublishedLinks(links);
    }

    /** Remembers a vacancy as published, so later runs no longer see it as new. */
    public void addPublishedVacancy(String link, int companyId, LocalDateTime crawledAt) {
        crawledVacancyRepository.save(CrawledVacancy.builder()
                .link(link)
                .companyId(companyId)
                .crawledAt(crawledAt)
                .build());
    }

    /** Starts the grace period of vacancies that are gone from their site. */
    @Transactional
    public void markOutdated(Collection<String> links, LocalDateTime deleteAt) {
        if (!links.isEmpty()) {
            crawledVacancyRepository.updateDeleteAt(links, deleteAt);
        }
    }

    /** Links of the vacancies whose grace period has ended by now, see {@link #markOutdated}. */
    public List<String> findExpiredLinks(LocalDateTime now) {
        return crawledVacancyRepository.findExpiredLinks(now);
    }

    /** Lifts the deletion marker of vacancies that showed up on their site again. */
    @Transactional
    public void restoreOutdated(Collection<String> links) {
        if (!links.isEmpty()) {
            crawledVacancyRepository.updateDeleteAt(links, null);
        }
    }

    @Transactional
    public void deleteVacancies(Collection<String> links) {
        if (!links.isEmpty()) {
            crawledVacancyRepository.deleteByLinks(links);
        }
    }
}
