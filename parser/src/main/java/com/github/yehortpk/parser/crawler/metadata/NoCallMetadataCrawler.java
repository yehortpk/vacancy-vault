package com.github.yehortpk.parser.crawler.metadata;

import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.FetchSettings;
import com.github.yehortpk.parser.models.MetadataParams;
import com.github.yehortpk.parser.scraper.PageScraper;
import com.github.yehortpk.parser.scraper.PageScraperResponse;

import java.util.Map;

/**
 * Makes no call: its scraper returns an empty response without a request. The company has a single page and no
 * placeholders besides {@code {page}}.
 */
public class NoCallMetadataCrawler implements CompanyMetadataCrawler {
    @Override
    public FetchSettings configureFetchSettings(CompanyDTO company) {
        return FetchSettings.builder().build();
    }

    @Override
    public PageScraper configurePageScraper() {
        return settings -> new PageScraperResponse(0, null, Map.of(), Map.of(), "");
    }

    @Override
    public MetadataParams parseMetadata(PageScraperResponse response) {
        return MetadataParams.builder().pagesCount(1).build();
    }
}
