package com.github.yehortpk.dashboard.services;

import com.github.yehortpk.dashboard.exception.VacancyNotFoundException;
import com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancySearchCriteria;
import com.github.yehortpk.dashboard.repositories.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This service provides methods for control vacancies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyService {
    private final VacancyRepository vacancyRepository;

    private static Vacancy createVacancy(NewVacancyDTO vacancy) {
        NewVacancyDTO.Company company = vacancy.getCompany();
        return Vacancy.builder()
                .link(vacancy.getLink())
                .title(vacancy.getTitle())
                .description(vacancy.getDescription())
                .minSalary(vacancy.getMinSalary())
                .maxSalary(vacancy.getMaxSalary())
                .salaryCurrency(vacancy.getSalaryCurrency())
                .salaryPeriod(vacancy.getSalaryPeriod())
                .isRemote(vacancy.isRemote())
                .crawledAt(vacancy.getCrawledAt())
                .companyId(company.getId())
                .companyTitle(company.getTitle())
                .companyUrl(company.getUrl())
                .build();
    }

    /**
     * Stores a published vacancy. A vacancy whose link is already stored is discarded:
     * duplicates are expected under Kafka's at-least-once delivery, so they are not errors, and
     * the stored row, its company included, is left as it is.
     *
     * @return whether the vacancy was actually stored
     */
    @Transactional
    public boolean addVacancy(NewVacancyDTO vacancy) {
        if (vacancyRepository.existsByLink(vacancy.getLink())) {
            log.info("Vacancy {} is already stored, discarded", vacancy.getLink());
            return false;
        }

        vacancyRepository.save(createVacancy(vacancy));
        return true;
    }

    /**
     * Removes the vacancy the crawler dropped. A link that is not stored is not an error: the
     * removal may be redelivered, or the vacancy may never have reached this service.
     *
     * @return whether a vacancy was actually removed
     */
    @Transactional
    public boolean removeVacancy(String link) {
        return vacancyRepository.deleteByLink(link) > 0;
    }

    public Vacancy getVacancy(int id) {
        return vacancyRepository.findVacancyById(id).orElseThrow(VacancyNotFoundException::new);
    }

    public Page<Vacancy> getVacancies(VacancySearchCriteria criteria, Pageable pageable) {
        return vacancyRepository.search(criteria.getQuery(), criteria.isRemoteRequired(),
                criteria.isSalaryRequired(), pageable);
    }
}
