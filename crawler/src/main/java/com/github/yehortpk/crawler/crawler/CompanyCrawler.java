package com.github.yehortpk.crawler.crawler;

import com.github.yehortpk.crawler.exceptions.VacancyPageParseException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;

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
     * Parses the vacancies of one fetched page, including any call made for each vacancy's own page
     *
     * @param page the fetched page
     * @param company the company the page belongs to
     * @return the page's vacancies
     * @throws VacancyPageParseException when the page can't be parsed
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
