package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.company.Company;
import com.github.yehortpk.crawler.models.company.CompanyCookie;
import com.github.yehortpk.crawler.models.company.CompanyData;
import com.github.yehortpk.crawler.models.company.CompanyHeader;
import com.github.yehortpk.crawler.repositories.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service provides methods for working with {@link CompanyRepository}
 */
@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;

    private static CompanyDTO createCompanyDTO(Company company) {
        return CompanyDTO.builder()
                .companyId(company.getCompanyId())
                .beanClass(company.getBeanClass())
                .title(company.getTitle())
                .mainPageURL(company.getMainPageURL())
                .vacanciesURL(company.getVacanciesURL())
                .apiVacanciesURL(company.getApiVacanciesURL())
                .isCrawlingEnabled(company.isCrawlingEnabled())
                .data(company.getCompanyData().stream()
                        .collect(Collectors.toMap(CompanyData::getKey, CompanyData::getValue)))
                .headers(company.getCompanyHeaders().stream()
                        .collect(Collectors.toMap(CompanyHeader::getKey, CompanyHeader::getValue)))
                .cookies(company.getCompanyCookies().stream()
                        .collect(Collectors.toMap(CompanyCookie::getKey, CompanyCookie::getValue)))
                .build();
    }

    /**
     * Returns the companies that are switched on for crawling
     * @return companies list
     */
    @Transactional(readOnly = true)
    public List<CompanyDTO> getCompaniesList() {
        return companyRepository.findCrawlingEnabled().stream()
                .map(CompanyService::createCompanyDTO)
                .toList();
    }
}
