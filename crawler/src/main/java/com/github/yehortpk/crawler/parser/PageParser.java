package com.github.yehortpk.crawler.parser;

import com.github.yehortpk.crawler.exceptions.VacancyPageParseException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;

import java.util.List;

/**
 * Turns one fetched vacancies page into its vacancies, as the page itself describes them. Parsing only: the call made
 * for each vacancy's own page is the crawler's job. A company crawler implements it in a private nested class and
 * returns a new instance of it from {@code configurePageParser()}.
 */
public interface PageParser {
    /**
     * Parses the page's vacancies
     *
     * @param page the fetched page
     * @param company the company the page belongs to
     * @return the page's vacancies
     * @throws VacancyPageParseException when the page can't be parsed
     */
    List<VacancyDTO> parsePage(PageScraperResponse page, CompanyDTO company);
}
