package com.github.yehortpk.router.services;

import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.repositories.VacancyRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * This service provides methods for control vacancies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyService {
    private final VacancyRepository vacancyRepository;
    private final ModelMapper modelMapper;

    /**
     * Persists all the vacancies from the input list
     * @param vacancies vacancies to persisting
     */
    @Transactional
    public void addVacancies(List<VacancyDTO> vacancies) {
        List<Long> existentVacanciesID = vacancyRepository.findAllIds();
        List<Vacancy> newVacancies = new ArrayList<>();
        for (VacancyDTO vacancy : vacancies) {
            if (existentVacanciesID.contains((long) vacancy.getVacancyID())) {
                continue;
            }

            Vacancy newVacancy = modelMapper.map(vacancy, Vacancy.class);

            newVacancies.add(newVacancy);
        }
        vacancyRepository.saveAll(newVacancies);
    }

    /**
     * Persists a single vacancy, skipping it if its link is already known. Duplicates are expected
     * under Kafka's at-least-once delivery, so they are not treated as errors.
     * @return whether the vacancy was actually persisted
     */
    public boolean addVacancy(VacancyDTO vacancy) {
        if (vacancyRepository.existsByLink(vacancy.getLink())) {
            log.debug("Vacancy {} already persisted, skipping", vacancy.getLink());
            return false;
        }

        Vacancy newVacancy = modelMapper.map(vacancy, Vacancy.class);

        try {
            vacancyRepository.save(newVacancy);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Backstop for the race between the check above and this insert
            log.debug("Duplicated vacancy {}", vacancy.getLink());
            return false;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    public List<Vacancy> getAllVacancies() {
        return vacancyRepository.findAll();
    }

    public Page<Vacancy> getVacancies(Pageable pageable) {
        return vacancyRepository.findAll(pageable);
    }

    public Page<Vacancy> getVacancies(String query, PageRequest pageable) {
        return vacancyRepository.findByTitleContaining(query, pageable);
    }

    public void updateVacancy(VacancyDTO vacancy) {
        Vacancy updatedVacancy = modelMapper.map(vacancy, Vacancy.class);
        vacancyRepository.save(updatedVacancy);
    }

    public void removeVacancy(Long id) {
        vacancyRepository.deleteById(id);
    }
}
