package com.github.yehortpk.dashboard.controllers;

import com.github.yehortpk.dashboard.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancyDetailsDTO;
import com.github.yehortpk.dashboard.models.vacancy.VacancySearchCriteria;
import com.github.yehortpk.dashboard.services.VacancyService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller responsible for the public vacancy listing and a single vacancy's page. Read-only:
 * vacancies arrive and leave over Kafka.
 */
@RestController
@RequestMapping("/vacancy")
@RequiredArgsConstructor
public class VacancyController {
    private final VacancyService vacancyService;
    private final ModelMapper modelMapper;

    @GetMapping
    public VacanciesPageDTO getVacanciesByPage(@RequestParam(value = "page") int pageId,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                               @RequestParam(value = "query", required = false) String query,
                                               @RequestParam(value = "remote", defaultValue = "false") boolean remoteRequired,
                                               @RequestParam(value = "withSalary", defaultValue = "false") boolean salaryRequired,
                                               @RequestParam(value = "country", required = false) String country
    ){

        // Newest first; the id keeps pages stable
        PageRequest pageable = PageRequest.of(pageId, pageSize,
                Sort.by(Sort.Order.desc("crawledAt").nullsLast(), Sort.Order.desc("vacancyID")));

        Page<Vacancy> vacanciesOnPage = vacancyService.getVacancies(
                new VacancySearchCriteria(query, remoteRequired, salaryRequired, country), pageable);

        return modelMapper.getTypeMap(Page.class, VacanciesPageDTO.class).map(vacanciesOnPage);
    }

    /** The countries the listing can be narrowed to: those of the stored vacancies, as ISO 3166-1 alpha-2 codes. */
    @GetMapping("/countries")
    public List<String> getCountries() {
        return vacancyService.getCountries();
    }

    @GetMapping("/{id}")
    public VacancyDetailsDTO getVacancy(@PathVariable int id) {
        return modelMapper.map(vacancyService.getVacancy(id), VacancyDetailsDTO.class);
    }
}
