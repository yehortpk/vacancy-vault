package com.github.yehortpk.crawler.scraper;

import com.github.yehortpk.crawler.models.ScrapeSettings;

import java.util.Map;

/**
 * Makes no request: returns an empty response, with no headers, cookies or body. For a call whose answer is already
 * known, such as a metadata call that only sets a fixed page count.
 */
public class NoCallPageScraper implements PageScraper {
    @Override
    public PageScraperResponse scrapePage(ScrapeSettings settings) {
        return new PageScraperResponse(settings.getPageId(), settings.getPageURL(), Map.of(), Map.of(), "");
    }
}
