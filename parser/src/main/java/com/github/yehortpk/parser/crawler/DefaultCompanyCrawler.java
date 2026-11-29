package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.crawler.metadata.CompanyMetadataCrawler;
import com.github.yehortpk.parser.crawler.metadata.NoMetadataCrawler;
import com.github.yehortpk.parser.exceptions.MetadataFetchException;
import com.github.yehortpk.parser.exceptions.MetadataParseException;
import com.github.yehortpk.parser.exceptions.ParsingStoppedException;
import com.github.yehortpk.parser.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.parser.exceptions.VacanciesFetchException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.FetchSettings;
import com.github.yehortpk.parser.models.MetadataParams;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.scraper.page.PageScraper;
import com.github.yehortpk.parser.scraper.page.PageScraperResponse;
import com.github.yehortpk.parser.scraper.page.StaticPageScraper;
import com.github.yehortpk.parser.services.ParsingProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs the whole fetch flow of a company: the metadata call, then every page. A company crawler extends it and only
 * describes its site: how each page is requested ({@link #configureFetchSettings}, plus the
 * {@link #configurePageScraper} and {@link #configureMetadataCrawler} hooks) and how a page turns into vacancies
 * ({@link #parseVacancies}). Holds no state of a run.
 */
@Slf4j
public abstract class DefaultCompanyCrawler implements CompanyCrawler {
    private static final String PAGE_PLACEHOLDER = "page";
    /**
     * Pages mostly wait on the network or on a free browser, so each gets a virtual thread of its
     * own. The default async pool would instead tie page concurrency to the host's CPU count.
     */
    private static final ExecutorService PAGE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    private ParsingProgressService parsingProgressService;

    /**
     * How the page is requested. Fields left {@code null} (page URL, data, headers, cookies) are filled from the
     * company; {@code pageId} is set by the caller.
     *
     * @param company the company to crawl
     * @param pageId the page, starting from 1
     * @return the page's fetch settings
     */
    protected abstract FetchSettings configureFetchSettings(CompanyDTO company, int pageId);

    /**
     * The scraper the page is fetched with, {@link StaticPageScraper} unless overridden
     *
     * @param pageId the page, starting from 1
     * @return a new scraper
     */
    protected PageScraper configurePageScraper(int pageId) {
        return new StaticPageScraper();
    }

    /**
     * The call made before the pages, {@link NoMetadataCrawler} unless overridden: one page and no placeholders
     * besides {@code {page}}
     *
     * @return a new metadata crawler, never {@code null}
     */
    protected CompanyMetadataCrawler configureMetadataCrawler() {
        return new NoMetadataCrawler();
    }

    /**
     * Makes the metadata call, builds the settings of every page and schedules the pages.
     *
     * @throws MetadataFetchException when the metadata call fails
     * @throws MetadataParseException when the metadata call's response can't be parsed
     * @throws UnresolvedPlaceholderException when a page has a placeholder without a value; no page is fetched then
     */
    @Override
    public List<CompletableFuture<PageScraperResponse>> scrapeCompanyVacancies(CompanyDTO company) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());

        MetadataParams metadata = requestMetadata(company);
        parserProgress.initPages(metadata.getPagesCount());

        // Every page is built before any is sent, so a placeholder error sends no request at all
        List<FetchSettings> pagesSettings = new ArrayList<>();
        for (int pageId = 1; pageId <= metadata.getPagesCount(); pageId++) {
            pagesSettings.add(createFetchSettings(company, pageId, metadata));
        }

        return pagesSettings.stream()
                .map(settings -> schedulePage(settings, company, parserProgress))
                .toList();
    }

    private MetadataParams requestMetadata(CompanyDTO company) {
        CompanyMetadataCrawler metadataCrawler = configureMetadataCrawler();

        PageScraperResponse response;
        try {
            response = metadataCrawler.extractMetadata(company);
        } catch (IOException | RuntimeException e) {
            throw new MetadataFetchException(
                    String.format("Metadata call of %s failed: %s", company.getTitle(), e.getMessage()), e);
        }

        MetadataParams metadata;
        try {
            metadata = metadataCrawler.parseMetadata(response);
        } catch (RuntimeException e) {
            throw new MetadataParseException(
                    String.format("Metadata of %s can't be parsed: %s", company.getTitle(), e.getMessage()), e);
        }

        // Zero pages would leave the company looking fully parsed while nothing was fetched
        if (metadata.getPagesCount() < 1) {
            throw new MetadataParseException(String.format("Metadata of %s has a page count of %d",
                    company.getTitle(), metadata.getPagesCount()), null);
        }
        return metadata;
    }

    private FetchSettings createFetchSettings(CompanyDTO company, int pageId, MetadataParams metadata) {
        FetchSettings configuredSettings = configureFetchSettings(company, pageId);

        FetchSettings settings = configuredSettings.toBuilder()
                .pageId(pageId)
                .pageURL(configuredSettings.getPageURL() != null
                        ? configuredSettings.getPageURL()
                        : company.getVacanciesURL())
                .data(copyValues(configuredSettings.getData() != null
                        ? configuredSettings.getData()
                        : company.getData()))
                .headers(mergeHeaders(configuredSettings.getHeaders() != null
                        ? configuredSettings.getHeaders()
                        : company.getHeaders(), metadata.getRequestHeaders()))
                .cookies(mergeCookies(configuredSettings.getCookies() != null
                        ? configuredSettings.getCookies()
                        : company.getCookies(), metadata.getRequestCookies()))
                .build();

        Map<String, String> placeholders = new HashMap<>(metadata.getPlaceholders());
        placeholders.put(PAGE_PLACEHOLDER, String.valueOf(pageId));

        return settings.fillPlaceholders(placeholders);
    }

    private static Map<String, String> copyValues(Map<String, String> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Header names are case-insensitive, so a metadata header replaces a configured one whatever its case
     */
    private static Map<String, String> mergeHeaders(Map<String, String> headers, Map<String, String> metadataHeaders) {
        Map<String, String> mergedHeaders = new LinkedHashMap<>(copyValues(headers));
        metadataHeaders.forEach((name, value) -> {
            mergedHeaders.keySet().removeIf(name::equalsIgnoreCase);
            mergedHeaders.put(name, value);
        });
        return Collections.unmodifiableMap(mergedHeaders);
    }

    private static Map<String, String> mergeCookies(Map<String, String> cookies, Map<String, String> metadataCookies) {
        Map<String, String> mergedCookies = new LinkedHashMap<>(copyValues(cookies));
        mergedCookies.putAll(metadataCookies);
        return Collections.unmodifiableMap(mergedCookies);
    }

    /**
     * Starts the page once its delay is over. A stop request ends the wait at once, so the page is skipped right
     * away.
     */
    private CompletableFuture<PageScraperResponse> schedulePage(FetchSettings settings, CompanyDTO company,
                                                                ParserProgress parserProgress) {
        return parsingProgressService.getStopSignal().copy()
                .completeOnTimeout(null, settings.getDelay().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(ignored -> scrapePage(settings, company, parserProgress), PAGE_EXECUTOR);
    }

    private PageScraperResponse scrapePage(FetchSettings settings, CompanyDTO company, ParserProgress parserProgress) {
        if (parsingProgressService.isStopRequested()) {
            throw new ParsingStoppedException();
        }

        int pageId = settings.getPageId();
        PageScraperResponse response;
        try {
            response = configurePageScraper(pageId).scrapePage(settings);
        } catch (IOException e) {
            throw new VacanciesFetchException(String.format("Page %d of %s can't be fetched: %s",
                    pageId, company.getTitle(), e.getMessage()), e);
        }

        String successLog = String.format("Company: %s, Page: %s, was scraped", company.getTitle(), pageId);
        parserProgress.addPageLog(pageId, ParserProgress.LogLevelEnum.INFO, successLog);
        log.info(successLog);

        return response;
    }
}
