package com.github.yehortpk.crawler.crawler.metadata;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.ScrapeSettings;
import com.github.yehortpk.crawler.models.MetadataParams;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
import com.github.yehortpk.crawler.scraper.NoCallPageScraper;


/**
 * Makes no call: its scraper returns an empty response without a request. The company has a single page and no
 * placeholders besides {@code {page}}.
 */
public class NoCallMetadataCrawler implements CompanyMetadataCrawler {
    @Override
    public ScrapeSettings configureScrapeSettings(CompanyDTO company) {
        return ScrapeSettings.builder().build();
    }

    @Override
    public PageScraper configurePageScraper() {
        return new NoCallPageScraper();
    }

    @Override
    public MetadataParams parseMetadata(PageScraperResponse response) {
        return MetadataParams.builder().pagesCount(1).build();
    }
}
