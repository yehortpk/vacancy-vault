package com.github.yehortpk.parser.crawler.metadata;

import com.github.yehortpk.parser.exceptions.MetadataParseException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.MetadataParams;
import com.github.yehortpk.parser.scraper.PageScraperResponse;

import java.io.IOException;

/**
 * The call a company crawler makes before its pages, to learn the page count, placeholder values, headers or
 * cookies. It builds its own fetch settings from the company and creates its own scraper. A company crawler that
 * needs a metadata call implements this interface in a private nested class and returns a new instance of it from
 * {@code configureMetadataCrawler()}.
 *
 * @see NoMetadataCrawler
 */
public interface CompanyMetadataCrawler {
    /**
     * Makes the metadata call
     *
     * @param company the company to crawl
     * @return the response to read the metadata from
     * @throws IOException when the metadata call fails
     */
    PageScraperResponse extractMetadata(CompanyDTO company) throws IOException;

    /**
     * Reads the metadata from the response of {@link #extractMetadata(CompanyDTO)}. When the response can't be parsed
     * it fails with whatever unchecked exception the parsing throws; {@code DefaultCompanyCrawler} wraps it in
     * {@link MetadataParseException}.
     *
     * @param response the metadata call's response
     * @return the metadata the pages are built with
     */
    MetadataParams parseMetadata(PageScraperResponse response);
}
