package com.github.yehortpk.parser.crawler.metadata;

import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.MetadataParams;
import com.github.yehortpk.parser.scraper.PageScraperResponse;

import java.util.Map;

/**
 * Makes no request: the company has a single page and no placeholders besides {@code {page}}.
 */
public class NoCallMetadataCrawler implements CompanyMetadataCrawler {
    @Override
    public PageScraperResponse extractMetadata(CompanyDTO company) {
        return new PageScraperResponse(0, null, Map.of(), Map.of(), "");
    }

    @Override
    public MetadataParams parseMetadata(PageScraperResponse response) {
        return MetadataParams.builder().pagesCount(1).build();
    }
}
