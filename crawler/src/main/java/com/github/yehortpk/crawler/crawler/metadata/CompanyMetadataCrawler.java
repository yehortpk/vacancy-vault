package com.github.yehortpk.crawler.crawler.metadata;

import com.github.yehortpk.crawler.exceptions.MetadataFetchException;
import com.github.yehortpk.crawler.exceptions.MetadataParseException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.FetchSettings;
import com.github.yehortpk.crawler.models.MetadataParams;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;

/**
 * The call a company crawler makes before its pages, to learn the page count, placeholder values, headers or
 * cookies. It only describes the call: {@code DefaultCompanyCrawler} fetches {@link #configureFetchSettings} with
 * {@link #configurePageScraper} and reads the response with {@link #parseMetadata}. A company crawler that needs a
 * metadata call implements this interface in a private nested class and returns a new instance of it from
 * {@code configureMetadataCrawler()}.
 *
 * @see NoCallMetadataCrawler
 */
public interface CompanyMetadataCrawler {
    /**
     * How the metadata call is requested. The settings are used as returned: nothing is filled from the company.
     *
     * @param company the company to crawl
     * @return the metadata call's fetch settings
     */
    FetchSettings configureFetchSettings(CompanyDTO company);

    /**
     * The scraper the metadata call is made with. When it fails, {@code DefaultCompanyCrawler} wraps the failure in
     * {@link MetadataFetchException}.
     *
     * @return a new scraper
     */
    PageScraper configurePageScraper();

    /**
     * Reads the metadata from the metadata call's response. When the response can't be parsed it fails with whatever
     * unchecked exception the parsing throws; {@code DefaultCompanyCrawler} wraps it in {@link MetadataParseException}.
     *
     * @param response the metadata call's response
     * @return the metadata the pages are built with
     */
    MetadataParams parseMetadata(PageScraperResponse response);
}
