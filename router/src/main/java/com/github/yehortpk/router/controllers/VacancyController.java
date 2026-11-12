package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.models.vacancy.VacancySearchCriteria;
import com.github.yehortpk.router.services.VacancyService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.PropertyUtils;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vacancy")
@RequiredArgsConstructor
public class VacancyController {
    private final VacancyService vacancyService;
    private final ModelMapper modelMapper;

    @PostMapping
    public void addVacancies(@RequestBody List<VacancyDTO> vacancies) {
        vacancyService.addVacancies(vacancies);
    }

    @PutMapping
    public void updateVacancy(@RequestBody VacancyDTO vacancy) {
        vacancyService.updateVacancy(vacancy);
    }

    @DeleteMapping("/{id}")
    public void removeVacancy(@PathVariable Long id) {
        vacancyService.removeVacancy(id);
    }

    @GetMapping
    @CrossOrigin("http://localhost:4200")
    public VacanciesPageDTO getVacanciesByPage(@RequestParam(value = "page") int pageId,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                               @RequestParam(value = "sortBy", defaultValue = "parsedAt") String sortBy,
                                               @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir,
                                               @RequestParam(value = "query", required = false) String query,
                                               @RequestParam(value = "remote", defaultValue = "false") boolean remoteRequired,
                                               @RequestParam(value = "withSalary", defaultValue = "false") boolean salaryRequired
    ){

        // Check if field exists in Vacancy entity
        if (!PropertyUtils.isReadable(new Vacancy(), sortBy)) {
            throw new IllegalArgumentException(String.format("Property %s doesn't exist in the sorted entity", sortBy));
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.valueOf(sortDir);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Use only ASC/DESC for sortDir property");
        }

        PageRequest pageable = PageRequest.of(pageId, pageSize,
                Sort.by(direction, sortBy));

        Page<Vacancy> vacanciesOnPage = vacancyService.getVacancies(
                new VacancySearchCriteria(query, remoteRequired, salaryRequired), pageable);

        return modelMapper.getTypeMap(Page.class, VacanciesPageDTO.class).map(vacanciesOnPage);
    }

    @GetMapping(params = "byCompany=true")
    public Map<Long, Set<String>> getPersistedVacanciesUrlsByCompanyId() {
        return vacancyService.getAllVacancies().stream().collect(Collectors.groupingBy(
                (vacancy -> (long) vacancy.getCompany().getCompanyId()),
                Collectors.mapping(Vacancy::getLink, Collectors.toSet())
        ));
    }
}
