package com.github.yehortpk.dashboard.services;

import com.github.yehortpk.dashboard.exception.CompanyNotFoundException;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.repositories.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Reads a company from its stored vacancies: the dashboard keeps no company of its own, each
 * vacancy carries its company's id, title and URL.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "crawledAt");

    private final VacancyRepository vacancyRepository;

    /**
     * The company's newest stored vacancy, which carries the company as the crawler last
     * published it.
     *
     * @throws CompanyNotFoundException when no stored vacancy belongs to the company
     */
    public Vacancy getCompany(int companyId) {
        return getCompanyVacancies(companyId, 0, 1).stream()
                .findFirst()
                .orElseThrow(CompanyNotFoundException::new);
    }

    /** One page of the company's vacancies, newest first. */
    public Page<Vacancy> getCompanyVacancies(int companyId, int pageId, int pageSize) {
        return vacancyRepository.findCompanyVacancies(companyId, PageRequest.of(pageId, pageSize, NEWEST_FIRST));
    }
}
