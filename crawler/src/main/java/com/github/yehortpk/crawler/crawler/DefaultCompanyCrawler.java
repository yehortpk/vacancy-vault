package com.github.yehortpk.crawler.crawler;

import com.github.yehortpk.crawler.crawler.metadata.CompanyMetadataCrawler;
import com.github.yehortpk.crawler.crawler.metadata.NoCallMetadataCrawler;
import com.github.yehortpk.crawler.exceptions.MetadataScrapeException;
import com.github.yehortpk.crawler.exceptions.MetadataParseException;
import com.github.yehortpk.crawler.exceptions.CrawlingStoppedException;
import com.github.yehortpk.crawler.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.crawler.crawler.vacancy.NoCallVacancyCrawler;
import com.github.yehortpk.crawler.crawler.vacancy.VacancyCrawler;
import com.github.yehortpk.crawler.exceptions.VacancyScrapeException;
import com.github.yehortpk.crawler.exceptions.VacancyPageScrapeException;
import com.github.yehortpk.crawler.exceptions.VacancyPageParseException;
import com.github.yehortpk.crawler.exceptions.VacancyParseException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.ScrapeSettings;
import com.github.yehortpk.crawler.models.MetadataParams;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.progress.CrawlerProgress;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
import com.github.yehortpk.crawler.scraper.StaticPageScraper;
import com.github.yehortpk.crawler.services.CrawlingProgressService;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs the whole fetch flow of a company: the metadata call, every page, then the call of every vacancy. A company
 * crawler extends it and only describes its site: how each page is requested ({@link #configureScrapeSettings}, plus
 * the {@link #configurePageScraper} and {@link #configureMetadataCrawler} hooks), how a page turns into vacancies
 * ({@link #parsePreviewVacancies}) and how each vacancy's own page is read ({@link #configureVacancyCrawler} hook).
 * Holds no state of a run.
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
    private CrawlingProgressService crawlingProgressService;

    /**
     * How the page is requested. The page URL is used as set here and never filled from the company; data, headers
     * and cookies left {@code null} are filled from the company. {@code pageId} is set by the caller.
     *
     * @param company the company to crawl
     * @param pageId the page, starting from 1
     * @return the page's fetch settings
     */
    protected abstract ScrapeSettings configureScrapeSettings(CompanyDTO company, int pageId);

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
     * The call made before the pages, {@link NoCallMetadataCrawler} unless overridden: one page and no placeholders
     * besides {@code {page}}
     *
     * @return a new metadata crawler, never {@code null}
     */
    protected CompanyMetadataCrawler configureMetadataCrawler() {
        return new NoCallMetadataCrawler();
    }

    /**
     * The call made for every vacancy of a parsed page, {@link NoCallVacancyCrawler} unless overridden: no request,
     * and every vacancy stays as its vacancies page described it
     *
     * @return a new vacancy crawler, never {@code null}
     */
    protected VacancyCrawler configureVacancyCrawler() {
        return new NoCallVacancyCrawler();
    }

    /**
     * Turns a fetched vacancies page into its vacancies, as the page itself describes them
     *
     * @param page the fetched page
     * @param company the company the page belongs to
     * @return the page's vacancies
     * @throws VacancyPageParseException when the page can't be parsed
     */
    protected abstract List<VacancyDTO> parsePreviewVacancies(PageScraperResponse page, CompanyDTO company);

    /**
     * Parses the page's vacancies with {@link #parsePreviewVacancies}, then makes the {@link #configureVacancyCrawler}
     * call for every one of them, each on a virtual thread of its own after its delay, and waits for all of them.
     * Every result replaces its preview vacancy. A vacancy whose call fails, or is skipped by a stop request, keeps its
     * preview; the page itself never fails because of it.
     *
     * @throws VacancyPageParseException when the page can't be parsed
     */
    @Override
    public final List<VacancyDTO> parseVacancies(PageScraperResponse page, CompanyDTO company) {
        List<VacancyDTO> previewVacancies = parsePreviewVacancies(page, company);
        CrawlerProgress crawlerProgress = crawlingProgressService.getCrawlers().get(company.getCompanyId());

        List<CompletableFuture<VacancyDTO>> vacancyFutures = new ArrayList<>();
        for (int i = 0; i < previewVacancies.size(); i++) {
            vacancyFutures.add(scheduleVacancy(previewVacancies.get(i), i + 1, page.getPageId(), company,
                    crawlerProgress));
        }

        return vacancyFutures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * Makes the metadata call, builds the settings of every page and schedules the pages.
     *
     * @throws MetadataScrapeException when the metadata call fails
     * @throws MetadataParseException when the metadata call's response can't be parsed
     * @throws UnresolvedPlaceholderException when a page has a placeholder without a value; no page is fetched then
     */
    @Override
    public List<CompletableFuture<PageScraperResponse>> scrapeCompanyVacancies(CompanyDTO company) {
        CrawlerProgress crawlerProgress = crawlingProgressService.getCrawlers().get(company.getCompanyId());

        MetadataParams metadata = requestMetadata(company);
        crawlerProgress.initPages(metadata.getPagesCount());

        // Every page is built before any is sent, so a placeholder error sends no request at all
        List<ScrapeSettings> pagesSettings = new ArrayList<>();
        for (int pageId = 1; pageId <= metadata.getPagesCount(); pageId++) {
            pagesSettings.add(createScrapeSettings(company, pageId, metadata));
        }

        return pagesSettings.stream()
                .map(settings -> schedulePage(settings, company, crawlerProgress))
                .toList();
    }

    private MetadataParams requestMetadata(CompanyDTO company) {
        CompanyMetadataCrawler metadataCrawler = configureMetadataCrawler();

        PageScraperResponse response;
        try {
            response = metadataCrawler.configurePageScraper()
                    .scrapePage(metadataCrawler.configureScrapeSettings(company));
        } catch (IOException | RuntimeException e) {
            throw new MetadataScrapeException(
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

    private ScrapeSettings createScrapeSettings(CompanyDTO company, int pageId, MetadataParams metadata) {
        ScrapeSettings configuredSettings = configureScrapeSettings(company, pageId);

        ScrapeSettings settings = configuredSettings.toBuilder()
                .pageId(pageId)
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
    private CompletableFuture<PageScraperResponse> schedulePage(ScrapeSettings settings, CompanyDTO company,
                                                                CrawlerProgress crawlerProgress) {
        return crawlingProgressService.getStopSignal().copy()
                .completeOnTimeout(null, settings.getDelay().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(ignored -> scrapePage(settings, company, crawlerProgress), PAGE_EXECUTOR);
    }

    private PageScraperResponse scrapePage(ScrapeSettings settings, CompanyDTO company, CrawlerProgress crawlerProgress) {
        if (crawlingProgressService.isStopRequested()) {
            throw new CrawlingStoppedException();
        }

        int pageId = settings.getPageId();
        PageScraperResponse response;
        try {
            response = configurePageScraper(pageId).scrapePage(settings);
        } catch (IOException e) {
            throw new VacancyPageScrapeException(String.format("Page %d of %s can't be fetched: %s",
                    pageId, company.getTitle(), e.getMessage()), e);
        }

        String successLog = String.format("Company: %s, Page: %s, was scraped", company.getTitle(), pageId);
        crawlerProgress.addPageLog(pageId, CrawlerProgress.LogLevelEnum.INFO, successLog);
        log.info(successLog);

        return response;
    }

    /**
     * Starts the vacancy's call once its delay is over, like {@link #schedulePage}. Never completes exceptionally: a
     * failed or skipped call completes with the preview vacancy.
     */
    private CompletableFuture<VacancyDTO> scheduleVacancy(VacancyDTO previewVacancy, int vacancyOrderOnPage, int pageId,
                                                          CompanyDTO company, CrawlerProgress crawlerProgress) {
        VacancyCrawler vacancyCrawler = configureVacancyCrawler();

        ScrapeSettings settings;
        try {
            settings = vacancyCrawler.configureScrapeSettings(previewVacancy, company, vacancyOrderOnPage);
        } catch (RuntimeException e) {
            VacancyScrapeException exception = new VacancyScrapeException(String.format(
                    "Vacancy %d of page %d of %s can't be configured: %s",
                    vacancyOrderOnPage, pageId, company.getTitle(), e.getMessage()), e);
            return CompletableFuture.completedFuture(
                    keepPreviewVacancy(exception, previewVacancy, vacancyOrderOnPage, pageId, company, crawlerProgress));
        }

        return crawlingProgressService.getStopSignal().copy()
                .completeOnTimeout(null, settings.getDelay().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(ignored -> scrapeVacancy(vacancyCrawler, settings, previewVacancy, vacancyOrderOnPage,
                        pageId, company, crawlerProgress), PAGE_EXECUTOR)
                .exceptionally(ex -> keepPreviewVacancy(ex, previewVacancy, vacancyOrderOnPage, pageId, company,
                        crawlerProgress));
    }

    private VacancyDTO scrapeVacancy(VacancyCrawler vacancyCrawler, ScrapeSettings settings, VacancyDTO previewVacancy,
                                     int vacancyOrderOnPage, int pageId, CompanyDTO company,
                                     CrawlerProgress crawlerProgress) {
        if (crawlingProgressService.isStopRequested()) {
            throw new CrawlingStoppedException();
        }

        PageScraperResponse response;
        try {
            response = vacancyCrawler.configurePageScraper().scrapePage(settings);
        } catch (IOException | RuntimeException e) {
            throw new VacancyScrapeException(String.format("Vacancy %d of page %d of %s can't be fetched: %s",
                    vacancyOrderOnPage, pageId, company.getTitle(), e.getMessage()), e);
        }

        VacancyDTO vacancy;
        try {
            vacancy = vacancyCrawler.parseVacancy(previewVacancy, response);
        } catch (RuntimeException e) {
            throw new VacancyParseException(String.format("Vacancy %d of page %d of %s can't be parsed: %s",
                    vacancyOrderOnPage, pageId, company.getTitle(), e.getMessage()), e);
        }
        if (vacancy == null) {
            throw new VacancyParseException(String.format("Vacancy %d of page %d of %s was parsed into null",
                    vacancyOrderOnPage, pageId, company.getTitle()), null);
        }

        String successLog = String.format("Company: %s, Page: %s, vacancy %s was scraped",
                company.getTitle(), pageId, vacancyOrderOnPage);
        crawlerProgress.addPageLog(pageId, CrawlerProgress.LogLevelEnum.INFO, successLog);
        log.info(successLog);

        return vacancy;
    }

    private VacancyDTO keepPreviewVacancy(Throwable ex, VacancyDTO previewVacancy, int vacancyOrderOnPage, int pageId,
                                          CompanyDTO company, CrawlerProgress crawlerProgress) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        String errorLog = String.format("Company: %s, Page: %s, vacancy %s kept its preview: %s",
                company.getTitle(), pageId, vacancyOrderOnPage, cause.getMessage());
        crawlerProgress.addPageLog(pageId, CrawlerProgress.LogLevelEnum.ERROR, errorLog);
        log.warn(errorLog);

        return previewVacancy;
    }
}
