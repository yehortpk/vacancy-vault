package com.github.yehortpk.crawler.crawler.vacancy;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.ScrapeSettings;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
import com.github.yehortpk.crawler.scraper.NoCallPageScraper;


/**
 * Makes no call: its scraper returns an empty response without a request, and the vacancy stays as the vacancies
 * page described it.
 */
public class NoCallVacancyDetailsCrawler implements VacancyDetailsCrawler {
    @Override
    public ScrapeSettings configureScrapeSettings(VacancyDTO previewVacancy, CompanyDTO company, int vacancyOrderOnPage) {
        return ScrapeSettings.builder().build();
    }

    @Override
    public PageScraper configurePageScraper() {
        return new NoCallPageScraper();
    }

    @Override
    public VacancyDTO parseVacancyDetails(VacancyDTO previewVacancy, PageScraperResponse response) {
        return previewVacancy.toBuilder().build();
    }
}
