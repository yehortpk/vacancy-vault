package com.github.yehortpk.crawler.scraper;

import com.github.yehortpk.crawler.models.ScrapeSettings;

import java.io.IOException;

/**
 * Fetches one page. I/O only: waiting between pages and stopping are the crawler's job, and nothing is retried.
 * Implementations are plain classes created with {@code new}, not beans.
 *
 * @see StaticPageScraper
 * @see DynamicPageScraper
 */
public interface PageScraper {
    /**
     * Fetches the page described by the settings. A POST whose {@code Content-Type} header is
     * {@code application/json} sends {@code data} as a JSON object of strings; otherwise {@code data} is the query
     * string of a GET or the form body of a POST.
     *
     * @param settings how the page is requested, with every placeholder already filled
     * @return the response, carrying the settings' page id and URL
     */
    PageScraperResponse scrapePage(ScrapeSettings settings) throws IOException;
}
