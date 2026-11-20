package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.models.company.Company;
import com.github.yehortpk.parser.models.company.CompanyCookie;
import com.github.yehortpk.parser.models.company.CompanyData;
import com.github.yehortpk.parser.models.company.CompanyHeader;
import com.github.yehortpk.parser.repositories.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This service provides methods for working with {@link CompanyRepository}
 */
@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;

    @Value("${router-companies-url}")
    private String routerCompaniesURL;

    @Value("${router-vacancies-url}")
    private String routerVacanciesURL;

    private static CompanyDTO createCompanyDTO(Company company) {
        return CompanyDTO.builder()
                .companyId(company.getCompanyId())
                .beanClass(company.getBeanClass())
                .title(company.getTitle())
                .mainPageURL(company.getMainPageURL())
                .vacanciesURL(company.getVacanciesURL())
                .apiVacanciesURL(company.getApiVacanciesURL())
                .isParsingEnabled(company.isParsingEnabled())
                .data(company.getCompanyData().stream()
                        .collect(Collectors.toMap(CompanyData::getKey, CompanyData::getValue)))
                .headers(company.getCompanyHeaders().stream()
                        .collect(Collectors.toMap(CompanyHeader::getKey, CompanyHeader::getValue)))
                .cookies(company.getCompanyCookies().stream()
                        .collect(Collectors.toMap(CompanyCookie::getKey, CompanyCookie::getValue)))
                .build();
    }

    /**
     * Returns the companies that are switched on for parsing
     * @return companies list
     */
    @Transactional(readOnly = true)
    public List<CompanyDTO> getCompaniesList() {
        return companyRepository.findParsingEnabled().stream()
                .map(CompanyService::createCompanyDTO)
                .toList();
    }

    /**
     * Returns persisted vacancies from specific company
     * @param companyId id of company
     * @return list of persisted vacancies
     */
    public List<VacancyDTO> getPersistedVacancies(int companyId) {
        ParameterizedTypeReference<List<VacancyDTO>> parameterizedTypeReference
                = new ParameterizedTypeReference<>() {
        };

        String finalURL = routerCompaniesURL + "/%s/vacancies".formatted(companyId);

        return restTemplate.exchange(finalURL, HttpMethod.GET, null, parameterizedTypeReference).getBody();
    }

    public Map<Long, Set<String>> getPersistedVacanciesUrlsByCompanyId() {
        ParameterizedTypeReference<Map<Long, Set<String>>> parameterizedTypeReference
                = new ParameterizedTypeReference<>() {
        };

        String finalURL = routerVacanciesURL + "?byCompany=true";

        Map<Long, Set<String>> body = restTemplate.exchange(finalURL, HttpMethod.GET, null, parameterizedTypeReference).getBody();
        if (body == null) {
            return new HashMap<>();
        }

        return body;
    }
}
