package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.exceptions.VacanciesParseException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.scraper.PageScraperResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches and parses the vacancies of one company. Registered as a bean named after the company's
 * {@code bean_class}. Holds no state of a run, so one bean may serve several companies.
 *
 * @see DefaultCompanyCrawler
 */
public interface CompanyCrawler {
    /**
     * Parses the vacancies of one fetched page
     *
     * @param page the fetched page
     * @param company the company the page belongs to
     * @return the page's vacancies
     * @throws VacanciesParseException when the page can't be parsed
     */
    List<VacancyDTO> parseVacancies(PageScraperResponse page, CompanyDTO company);

    /**
     * Starts fetching every page of the company
     *
     * @param company the company to fetch
     * @return one future per page, in page order
     */
    List<CompletableFuture<PageScraperResponse>> scrapeCompanyVacancies(CompanyDTO company);
}
