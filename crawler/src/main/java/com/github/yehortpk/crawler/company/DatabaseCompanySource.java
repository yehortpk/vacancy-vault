package com.github.yehortpk.crawler.company;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.company.Company;
import com.github.yehortpk.crawler.models.company.CompanyCookie;
import com.github.yehortpk.crawler.models.company.CompanyData;
import com.github.yehortpk.crawler.models.company.CompanyHeader;
import com.github.yehortpk.crawler.repositories.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The default {@link CompanySource}: the companies of the {@code company} table that are switched on
 * for crawling. Skipped when the application has its own {@code CompanySource} bean.
 */
@Component
@RequiredArgsConstructor
public class DatabaseCompanySource implements CompanySource {
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

    @Override
    @Transactional(readOnly = true)
    public List<CompanyDTO> getCompanies() {
        return companyRepository.findCrawlingEnabled().stream()
                .map(DatabaseCompanySource::createCompanyDTO)
                .toList();
    }
}
