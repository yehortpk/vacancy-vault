package com.github.yehortpk.crawler.crawler.vacancy;

import com.github.yehortpk.crawler.exceptions.VacancyFetchException;
import com.github.yehortpk.crawler.exceptions.VacancyParseException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.FetchSettings;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;

/**
 * The call a company crawler makes for every vacancy of a parsed page, to read the vacancy's own page. It only
 * describes the call: {@code DefaultCompanyCrawler} fetches {@link #configureFetchSettings} with
 * {@link #configurePageScraper} and reads the response with {@link #parseVacancy}. A company crawler that needs it
 * implements this interface in a private nested class and returns a new instance of it from
 * {@code configureVacancyCrawler()}.
 * <p>
 * Every vacancy of every page gets a call on every run, which multiplies the requests sent to the company's site.
 *
 * @see NoCallVacancyCrawler
 */
public interface VacancyCrawler {
    /**
     * How the vacancy's page is requested. The settings are used as returned: nothing is filled from the vacancy or
     * the company. The delay counts from the moment the page's vacancies are scheduled.
     *
     * @param previewVacancy the vacancy as parsed from the vacancies page
     * @param company the company to crawl
     * @param vacancyOrderOnPage the vacancy's place on its page, starting from 1
     * @return the vacancy page's fetch settings
     */
    FetchSettings configureFetchSettings(VacancyDTO previewVacancy, CompanyDTO company, int vacancyOrderOnPage);

    /**
     * The scraper the vacancy's page is fetched with. When it fails, {@code DefaultCompanyCrawler} wraps the failure
     * in {@link VacancyFetchException} and keeps the preview vacancy.
     *
     * @return a new scraper
     */
    PageScraper configurePageScraper();

    /**
     * Reads the vacancy from its page. The returned vacancy replaces the preview one, so start from
     * {@code previewVacancy.toBuilder()} and set only what the page adds. Keep the {@code link}: it is the vacancy's
     * identity, and a different link makes it another vacancy. When the page can't be parsed, or {@code null} is
     * returned, {@code DefaultCompanyCrawler} wraps the failure in {@link VacancyParseException} and keeps the
     * preview vacancy.
     *
     * @param previewVacancy the vacancy as parsed from the vacancies page
     * @param response the vacancy page's response
     * @return the vacancy that replaces the preview one
     */
    VacancyDTO parseVacancy(VacancyDTO previewVacancy, PageScraperResponse response);
}
