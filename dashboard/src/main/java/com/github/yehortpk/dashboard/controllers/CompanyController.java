package com.github.yehortpk.dashboard.controllers;

import com.github.yehortpk.dashboard.models.company.CompanyShortInfoDTO;
import com.github.yehortpk.dashboard.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.services.CompanyService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for a company's page: the company and its stored vacancies. Read-only,
 * and a company is known here only while at least one of its vacancies is stored.
 */
@RestController
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;
    private final ModelMapper modelMapper;

    @GetMapping("/{id}")
    public CompanyShortInfoDTO getCompany(@PathVariable int id) {
        return modelMapper.map(companyService.getCompany(id), CompanyShortInfoDTO.class);
    }

    @GetMapping("/{id}/vacancies")
    public VacanciesPageDTO getCompanyVacancies(@PathVariable int id,
                                                @RequestParam(value = "page") int pageId,
                                                @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Page<Vacancy> vacanciesOnPage = companyService.getCompanyVacancies(id, pageId, pageSize);

        return modelMapper.getTypeMap(Page.class, VacanciesPageDTO.class).map(vacanciesOnPage);
    }
}
