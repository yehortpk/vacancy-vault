package com.github.yehortpk.dashboard.services;

import com.github.yehortpk.dashboard.exception.VacancyNotFoundException;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancySearchCriteria;
import com.github.yehortpk.dashboard.repositories.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * This service provides methods for control vacancies
 */
@Service
@RequiredArgsConstructor
public class VacancyService {
    private final VacancyRepository vacancyRepository;

    public Vacancy getVacancy(int id) {
        return vacancyRepository.findVacancyById(id).orElseThrow(VacancyNotFoundException::new);
    }

    public Page<Vacancy> getVacancies(VacancySearchCriteria criteria, Pageable pageable) {
        return vacancyRepository.search(criteria.getQuery(), criteria.isRemoteRequired(),
                criteria.isSalaryRequired(), pageable);
    }
}
