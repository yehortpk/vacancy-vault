package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.crawler.metadata.CompanyMetadataCrawler;
import com.github.yehortpk.parser.exceptions.MetadataFetchException;
import com.github.yehortpk.parser.exceptions.MetadataParseException;
import com.github.yehortpk.parser.exceptions.ParsingStoppedException;
import com.github.yehortpk.parser.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.parser.exceptions.VacanciesFetchException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.FetchSettings;
import com.github.yehortpk.parser.models.MetadataParams;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.scraper.PageScraper;
import com.github.yehortpk.parser.scraper.PageScraperResponse;
import com.github.yehortpk.parser.services.ParsingProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCompanyCrawlerTest {
    private static final int TIMEOUT_SEC = 5;

    private final ParsingProgressService parsingProgressService = new ParsingProgressService();
    private final List<FetchSettings> scrapedSettings = new CopyOnWriteArrayList<>();
    private final PageScraper stubPageScraper = settings -> {
        scrapedSettings.add(settings);
        return new PageScraperResponse(settings.getPageId(), settings.getPageURL(), Map.of(), Map.of(), "body");
    };

    /**
     * A company crawler built from the parts each test needs
     */
    private static class TestCrawler extends DefaultCompanyCrawler {
        private final BiFunction<CompanyDTO, Integer, FetchSettings> fetchSettingsFactory;
        private final PageScraper pageScraper;
        private final CompanyMetadataCrawler metadataCrawler;

        TestCrawler(BiFunction<CompanyDTO, Integer, FetchSettings> fetchSettingsFactory, PageScraper pageScraper,
                    CompanyMetadataCrawler metadataCrawler) {
            this.fetchSettingsFactory = fetchSettingsFactory;
            this.pageScraper = pageScraper;
            this.metadataCrawler = metadataCrawler;
        }

        @Override
        protected FetchSettings configureFetchSettings(CompanyDTO company, int pageId) {
            return fetchSettingsFactory.apply(company, pageId);
        }

        @Override
        protected PageScraper configurePageScraper(int pageId) {
            return pageScraper;
        }

        @Override
        protected CompanyMetadataCrawler configureMetadataCrawler() {
            return metadataCrawler != null ? metadataCrawler : super.configureMetadataCrawler();
        }

        @Override
        public List<VacancyDTO> parseVacancies(PageScraperResponse page, CompanyDTO company) {
            return List.of();
        }
    }

    /**
     * A metadata crawler that returns the given metadata without a request
     */
    private static CompanyMetadataCrawler createMetadataCrawler(MetadataParams metadata) {
        return new CompanyMetadataCrawler() {
            @Override
            public PageScraperResponse extractMetadata(CompanyDTO company) {
                return new PageScraperResponse(0, null, Map.of(), Map.of(), "");
            }

            @Override
            public MetadataParams parseMetadata(PageScraperResponse response) {
                return metadata;
            }
        };
    }

    private TestCrawler createCrawler(BiFunction<CompanyDTO, Integer, FetchSettings> fetchSettingsFactory,
                                      CompanyMetadataCrawler metadataCrawler) {
        TestCrawler crawler = new TestCrawler(fetchSettingsFactory, stubPageScraper, metadataCrawler);
        ReflectionTestUtils.setField(crawler, "parsingProgressService", parsingProgressService);
        return crawler;
    }

    private TestCrawler createCrawler(CompanyMetadataCrawler metadataCrawler) {
        return createCrawler((company, pageId) -> FetchSettings.builder().build(), metadataCrawler);
    }

    private static CompanyDTO createCompany() {
        return CompanyDTO.builder()
                .companyId(1)
                .title("Company")
                .vacanciesURL("https://example.com/vacancies/{page}")
                .data(Map.of("page", "{page}", "city", "Kyiv"))
                .headers(Map.of("Accept", "text/html", "User-Agent", "configured"))
                .cookies(Map.of("session", "configured", "locale", "en"))
                .build();
    }

    private static List<PageScraperResponse> joinPages(List<CompletableFuture<PageScraperResponse>> pages) {
        return pages.stream().map(page -> page.orTimeout(TIMEOUT_SEC, TimeUnit.SECONDS).join()).toList();
    }

    private List<FetchSettings> getScrapedSettingsInPageOrder() {
        return scrapedSettings.stream().sorted(Comparator.comparingInt(FetchSettings::getPageId)).toList();
    }

    @BeforeEach
    void initProgress() {
        parsingProgressService.init();
        parsingProgressService.addParserProgress(1, "Company");
    }

    @Test
    void ScrapeCompanyVacancies_NoCallMetadataCrawler_ShouldScrapeOnePage() {
        List<PageScraperResponse> pages = joinPages(createCrawler(null).scrapeCompanyVacancies(createCompany()));

        assertEquals(1, pages.size());
        assertEquals(1, pages.getFirst().getPageId());
        assertEquals(1, parsingProgressService.getParsers().get(1).getTotalPages());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataPagesCount_ShouldScrapeThatManyPages() {
        TestCrawler crawler = createCrawler(createMetadataCrawler(MetadataParams.builder().pagesCount(3).build()));

        List<PageScraperResponse> pages = joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        assertEquals(List.of(1, 2, 3), pages.stream().map(PageScraperResponse::getPageId).toList());
        assertEquals(3, parsingProgressService.getParsers().get(1).getTotalPages());
    }

    @Test
    void ScrapeCompanyVacancies_SettingsPerPage_ShouldScrapeEachPageWithItsSettings() {
        TestCrawler crawler = createCrawler(
                (company, pageId) -> FetchSettings.builder()
                        .pageURL("https://example.com/api/" + pageId)
                        .timeoutSec(10 * pageId)
                        .build(),
                createMetadataCrawler(MetadataParams.builder().pagesCount(2).build()));

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        List<FetchSettings> settings = getScrapedSettingsInPageOrder();
        assertEquals("https://example.com/api/1", settings.get(0).getPageURL());
        assertEquals(10, settings.get(0).getTimeoutSec());
        assertEquals("https://example.com/api/2", settings.get(1).getPageURL());
        assertEquals(20, settings.get(1).getTimeoutSec());
    }

    @Test
    void ScrapeCompanyVacancies_PlaceholdersFromMetadata_ShouldFillThemWithPage() {
        CompanyDTO company = CompanyDTO.builder()
                .companyId(1)
                .title("Company")
                .vacanciesURL("https://example.com/vacancies/{page}")
                .data(Map.of("page", "{page}", "nonce", "{nonce}"))
                .headers(Map.of("X-Token", "{nonce}"))
                .cookies(Map.of())
                .build();
        TestCrawler crawler = createCrawler(createMetadataCrawler(
                MetadataParams.builder().pagesCount(2).placeholders(Map.of("nonce", "abc")).build()));

        joinPages(crawler.scrapeCompanyVacancies(company));

        FetchSettings secondPage = getScrapedSettingsInPageOrder().get(1);
        assertEquals("https://example.com/vacancies/2", secondPage.getPageURL());
        assertEquals(Map.of("page", "2", "nonce", "abc"), secondPage.getData());
        assertEquals(Map.of("X-Token", "abc"), secondPage.getHeaders());
    }

    @Test
    void ScrapeCompanyVacancies_UnresolvedPlaceholder_ShouldThrowBeforeAnyScrape() {
        CompanyDTO company = createCompany();
        company.setData(Map.of("nonce", "{nonce}"));
        TestCrawler crawler = createCrawler(null);

        UnresolvedPlaceholderException exception = assertThrows(UnresolvedPlaceholderException.class,
                () -> crawler.scrapeCompanyVacancies(company));

        assertEquals("{nonce}", exception.getPlaceholder());
        assertTrue(scrapedSettings.isEmpty());
    }

    @Test
    void ScrapeCompanyVacancies_ExtractMetadataThrowsIOException_ShouldWrapInMetadataFetchException() {
        IOException cause = new IOException("Connection refused");
        TestCrawler crawler = createCrawler(new CompanyMetadataCrawler() {
            @Override
            public PageScraperResponse extractMetadata(CompanyDTO company) throws IOException {
                throw cause;
            }

            @Override
            public MetadataParams parseMetadata(PageScraperResponse response) {
                return MetadataParams.builder().build();
            }
        });

        MetadataFetchException exception = assertThrows(MetadataFetchException.class,
                () -> crawler.scrapeCompanyVacancies(createCompany()));

        assertSame(cause, exception.getCause());
        assertTrue(scrapedSettings.isEmpty());
    }

    @Test
    void ScrapeCompanyVacancies_ExtractMetadataThrowsRuntimeException_ShouldWrapInMetadataFetchException() {
        TestCrawler crawler = createCrawler(new CompanyMetadataCrawler() {
            @Override
            public PageScraperResponse extractMetadata(CompanyDTO company) {
                throw new IllegalStateException("Browser crashed");
            }

            @Override
            public MetadataParams parseMetadata(PageScraperResponse response) {
                return MetadataParams.builder().build();
            }
        });

        MetadataFetchException exception = assertThrows(MetadataFetchException.class,
                () -> crawler.scrapeCompanyVacancies(createCompany()));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void ScrapeCompanyVacancies_ParseMetadataFails_ShouldWrapInMetadataParseException() {
        TestCrawler crawler = createCrawler(new CompanyMetadataCrawler() {
            @Override
            public PageScraperResponse extractMetadata(CompanyDTO company) {
                return new PageScraperResponse(0, null, Map.of(), Map.of(), "<html></html>");
            }

            @Override
            public MetadataParams parseMetadata(PageScraperResponse response) {
                throw new NullPointerException("No pagination element");
            }
        });

        MetadataParseException exception = assertThrows(MetadataParseException.class,
                () -> crawler.scrapeCompanyVacancies(createCompany()));

        assertInstanceOf(NullPointerException.class, exception.getCause());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataPagesCountBelowOne_ShouldThrowMetadataParseException() {
        TestCrawler crawler = createCrawler(createMetadataCrawler(MetadataParams.builder().pagesCount(0).build()));

        assertThrows(MetadataParseException.class, () -> crawler.scrapeCompanyVacancies(createCompany()));
        assertTrue(scrapedSettings.isEmpty());
    }

    @Test
    void ScrapeCompanyVacancies_StopRequestedDuringDelay_ShouldFailWaitingPageAtOnce() {
        TestCrawler crawler = createCrawler(
                (company, pageId) -> FetchSettings.builder().delay(Duration.ofHours(pageId - 1)).build(),
                createMetadataCrawler(MetadataParams.builder().pagesCount(2).build()));

        List<CompletableFuture<PageScraperResponse>> pages = crawler.scrapeCompanyVacancies(createCompany());
        CompletableFuture<PageScraperResponse> waitingPage = pages.get(1);
        pages.getFirst().orTimeout(TIMEOUT_SEC, TimeUnit.SECONDS).join();
        assertFalse(waitingPage.isDone());

        parsingProgressService.requestStop();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> waitingPage.get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertInstanceOf(ParsingStoppedException.class, exception.getCause());
    }

    @Test
    void ScrapeCompanyVacancies_UnsetFields_ShouldFillThemFromCompany() {
        joinPages(createCrawler(null).scrapeCompanyVacancies(createCompany()));

        FetchSettings settings = scrapedSettings.getFirst();
        assertEquals("https://example.com/vacancies/1", settings.getPageURL());
        assertEquals(Map.of("page", "1", "city", "Kyiv"), settings.getData());
        assertEquals(Map.of("Accept", "text/html", "User-Agent", "configured"), settings.getHeaders());
        assertEquals(Map.of("session", "configured", "locale", "en"), settings.getCookies());
    }

    @Test
    void ScrapeCompanyVacancies_EmptyMapsSetByCrawler_ShouldKeepThemEmpty() {
        TestCrawler crawler = createCrawler(
                (company, pageId) -> FetchSettings.builder().data(Map.of()).headers(Map.of()).cookies(Map.of()).build(),
                null);

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        FetchSettings settings = scrapedSettings.getFirst();
        assertEquals(Map.of(), settings.getData());
        assertEquals(Map.of(), settings.getHeaders());
        assertEquals(Map.of(), settings.getCookies());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataHeadersAndCookies_ShouldOverrideConfiguredOnesByKey() {
        TestCrawler crawler = createCrawler(createMetadataCrawler(MetadataParams.builder()
                .requestHeaders(Map.of("user-agent", "from metadata", "X-Csrf", "token"))
                .requestCookies(Map.of("session", "from metadata"))
                .build()));

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        FetchSettings settings = scrapedSettings.getFirst();
        assertEquals(Map.of("Accept", "text/html", "user-agent", "from metadata", "X-Csrf", "token"),
                settings.getHeaders());
        assertEquals(Map.of("session", "from metadata", "locale", "en"), settings.getCookies());
    }

    @Test
    void ScrapeCompanyVacancies_PageScraperThrowsIOException_ShouldFailPageWithVacanciesFetchException() {
        IOException cause = new IOException("Connection reset");
        TestCrawler crawler = new TestCrawler((company, pageId) -> FetchSettings.builder().build(),
                settings -> {
                    throw cause;
                }, null);
        ReflectionTestUtils.setField(crawler, "parsingProgressService", parsingProgressService);

        CompletableFuture<PageScraperResponse> page = crawler.scrapeCompanyVacancies(createCompany()).getFirst();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> page.get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertInstanceOf(VacanciesFetchException.class, exception.getCause());
        assertSame(cause, exception.getCause().getCause());
    }
}
