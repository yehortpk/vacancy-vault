package com.github.yehortpk.crawler.crawler.vacancy;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.FetchSettings;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;

import java.util.Map;

/**
 * Makes no call: its scraper returns an empty response without a request, and the vacancy stays as the vacancies
 * page described it.
 */
public class NoCallVacancyCrawler implements VacancyCrawler {
    @Override
    public FetchSettings configureFetchSettings(VacancyDTO previewVacancy, CompanyDTO company, int vacancyOrderOnPage) {
        return FetchSettings.builder().build();
    }

    @Override
    public PageScraper configurePageScraper() {
        return settings -> new PageScraperResponse(0, null, Map.of(), Map.of(), "");
    }

    @Override
    public VacancyDTO parseVacancy(VacancyDTO previewVacancy, PageScraperResponse response) {
        return previewVacancy.toBuilder().build();
    }
}
