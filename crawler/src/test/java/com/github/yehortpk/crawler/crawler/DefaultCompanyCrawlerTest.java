package com.github.yehortpk.crawler.crawler;

import com.github.yehortpk.crawler.crawler.metadata.CompanyMetadataCrawler;
import com.github.yehortpk.crawler.crawler.vacancy.VacancyDetailsCrawler;
import com.github.yehortpk.crawler.exceptions.MetadataScrapeException;
import com.github.yehortpk.crawler.exceptions.MetadataParseException;
import com.github.yehortpk.crawler.exceptions.CrawlingStoppedException;
import com.github.yehortpk.crawler.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.crawler.exceptions.VacancyPageScrapeException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.ScrapeSettings;
import com.github.yehortpk.crawler.models.MetadataParams;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.parser.PageParser;
import com.github.yehortpk.crawler.progress.CrawlerProgress;
import com.github.yehortpk.crawler.scraper.PageScraper;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
import com.github.yehortpk.crawler.services.CrawlingProgressService;
import com.github.yehortpk.crawler.scraper.NoCallPageScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCompanyCrawlerTest {
    private static final int TIMEOUT_SEC = 5;

    private final CrawlingProgressService crawlingProgressService = new CrawlingProgressService();
    private final List<ScrapeSettings> scrapedSettings = new CopyOnWriteArrayList<>();
    private final PageScraper stubPageScraper = settings -> {
        scrapedSettings.add(settings);
        return new PageScraperResponse(settings.getPageId(), settings.getPageURL(), Map.of(), Map.of(), "body");
    };

    /**
     * A company crawler built from the parts each test needs
     */
    private static class TestCrawler extends DefaultCompanyCrawler {
        private final BiFunction<CompanyDTO, Integer, ScrapeSettings> scrapeSettingsFactory;
        private final PageScraper pageScraper;
        private final CompanyMetadataCrawler metadataCrawler;
        private final List<VacancyDTO> previewVacancies;
        private final VacancyDetailsCrawler vacancyDetailsCrawler;

        TestCrawler(BiFunction<CompanyDTO, Integer, ScrapeSettings> scrapeSettingsFactory, PageScraper pageScraper,
                    CompanyMetadataCrawler metadataCrawler, List<VacancyDTO> previewVacancies,
                    VacancyDetailsCrawler vacancyDetailsCrawler) {
            this.scrapeSettingsFactory = scrapeSettingsFactory;
            this.pageScraper = pageScraper;
            this.metadataCrawler = metadataCrawler;
            this.previewVacancies = previewVacancies;
            this.vacancyDetailsCrawler = vacancyDetailsCrawler;
        }

        @Override
        protected ScrapeSettings configurePageScrapeSettings(CompanyDTO company, int pageId) {
            return scrapeSettingsFactory.apply(company, pageId);
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
        protected VacancyDetailsCrawler configureVacancyDetailsCrawler() {
            return vacancyDetailsCrawler != null ? vacancyDetailsCrawler : super.configureVacancyDetailsCrawler();
        }

        @Override
        protected PageParser configurePageParser() {
            return (page, company) -> previewVacancies;
        }
    }

    /**
     * A metadata crawler that returns the given metadata without a request
     */
    private static CompanyMetadataCrawler createMetadataCrawler(MetadataParams metadata) {
        return createMetadataCrawler(new NoCallPageScraper(), metadata);
    }

    /**
     * A metadata crawler whose call is made with the given scraper and that returns the given metadata
     */
    private static CompanyMetadataCrawler createMetadataCrawler(PageScraper metadataScraper, MetadataParams metadata) {
        return new CompanyMetadataCrawler() {
            @Override
            public ScrapeSettings configureScrapeSettings(CompanyDTO company) {
                return ScrapeSettings.builder().pageURL("https://example.com/metadata").build();
            }

            @Override
            public PageScraper configurePageScraper() {
                return metadataScraper;
            }

            @Override
            public MetadataParams parseMetadata(PageScraperResponse response) {
                return metadata;
            }
        };
    }

    /**
     * A vacancy crawler that requests the vacancy's link with the given scraper and sets the response body as the
     * vacancy's description
     */
    private static VacancyDetailsCrawler createVacancyDetailsCrawler(PageScraper vacancyScraper) {
        return new VacancyDetailsCrawler() {
            @Override
            public ScrapeSettings configureScrapeSettings(VacancyDTO previewVacancy, CompanyDTO company,
                                                        int vacancyOrderOnPage) {
                return ScrapeSettings.builder().pageURL(previewVacancy.getLink()).build();
            }

            @Override
            public PageScraper configurePageScraper() {
                return vacancyScraper;
            }

            @Override
            public VacancyDTO parseVacancyDetails(VacancyDTO previewVacancy, PageScraperResponse response) {
                return previewVacancy.toBuilder().description(response.getBody()).build();
            }
        };
    }

    private TestCrawler createCrawler(BiFunction<CompanyDTO, Integer, ScrapeSettings> scrapeSettingsFactory,
                                      CompanyMetadataCrawler metadataCrawler) {
        return initCrawler(new TestCrawler(scrapeSettingsFactory, stubPageScraper, metadataCrawler, List.of(), null));
    }

    private TestCrawler createCrawler(CompanyMetadataCrawler metadataCrawler) {
        return createCrawler((company, pageId) -> ScrapeSettings.builder().build(), metadataCrawler);
    }

    /**
     * A crawler whose page holds the given preview vacancies, with one initialized page in the progress
     */
    private TestCrawler createVacanciesCrawler(List<VacancyDTO> previewVacancies,
                                               VacancyDetailsCrawler vacancyDetailsCrawler) {
        crawlingProgressService.getCrawlers().get(1).initPages(1);
        return initCrawler(new TestCrawler((company, pageId) -> ScrapeSettings.builder().build(), stubPageScraper,
                null, previewVacancies, vacancyDetailsCrawler));
    }

    private TestCrawler initCrawler(TestCrawler crawler) {
        ReflectionTestUtils.setField(crawler, "crawlingProgressService", crawlingProgressService);
        return crawler;
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

    private static PageScraperResponse createVacanciesPage() {
        return new PageScraperResponse(1, "https://example.com/vacancies/1", Map.of(), Map.of(), "body");
    }

    private static VacancyDTO createPreviewVacancy(String link) {
        return VacancyDTO.builder().title("Title of " + link).link(link).build();
    }

    private static List<PageScraperResponse> joinPages(List<CompletableFuture<PageScraperResponse>> pages) {
        return pages.stream().map(page -> page.orTimeout(TIMEOUT_SEC, TimeUnit.SECONDS).join()).toList();
    }

    private List<ScrapeSettings> getScrapedSettingsInPageOrder() {
        return scrapedSettings.stream().sorted(Comparator.comparingInt(ScrapeSettings::getPageId)).toList();
    }

    private List<CrawlerProgress.PageProgress.PageLog> getFirstPageLogs() {
        return crawlingProgressService.getCrawlers().get(1).getPages().getFirst().getLogs();
    }

    @BeforeEach
    void initProgress() {
        crawlingProgressService.init();
        crawlingProgressService.addCrawlerProgress(1, "Company");
    }

    @Test
    void ScrapeCompanyVacancies_NoCallMetadataCrawler_ShouldScrapeOnePage() {
        List<PageScraperResponse> pages = joinPages(createCrawler(null).scrapeCompanyVacancies(createCompany()));

        assertEquals(1, pages.size());
        assertEquals(1, pages.getFirst().getPageId());
        assertEquals(1, crawlingProgressService.getCrawlers().get(1).getTotalPages());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataCrawler_ShouldScrapeWithItsSettingsAndScraper() {
        List<ScrapeSettings> metadataSettings = new CopyOnWriteArrayList<>();
        TestCrawler crawler = createCrawler(createMetadataCrawler(settings -> {
            metadataSettings.add(settings);
            return new PageScraperResponse(0, settings.getPageURL(), Map.of(), Map.of(), "");
        }, MetadataParams.builder().pagesCount(2).build()));

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        assertEquals(1, metadataSettings.size());
        assertEquals("https://example.com/metadata", metadataSettings.getFirst().getPageURL());
        assertEquals(2, scrapedSettings.size());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataPagesCount_ShouldScrapeThatManyPages() {
        TestCrawler crawler = createCrawler(createMetadataCrawler(MetadataParams.builder().pagesCount(3).build()));

        List<PageScraperResponse> pages = joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        assertEquals(List.of(1, 2, 3), pages.stream().map(PageScraperResponse::getPageId).toList());
        assertEquals(3, crawlingProgressService.getCrawlers().get(1).getTotalPages());
    }

    @Test
    void ScrapeCompanyVacancies_SettingsPerPage_ShouldScrapeEachPageWithItsSettings() {
        TestCrawler crawler = createCrawler(
                (company, pageId) -> ScrapeSettings.builder()
                        .pageURL("https://example.com/api/" + pageId)
                        .timeoutSec(10 * pageId)
                        .build(),
                createMetadataCrawler(MetadataParams.builder().pagesCount(2).build()));

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        List<ScrapeSettings> settings = getScrapedSettingsInPageOrder();
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
        TestCrawler crawler = createCrawler(
                (configuredCompany, pageId) -> ScrapeSettings.builder()
                        .pageURL(configuredCompany.getVacanciesURL())
                        .build(),
                createMetadataCrawler(MetadataParams.builder().pagesCount(2).placeholders(Map.of("nonce", "abc")).build()));

        joinPages(crawler.scrapeCompanyVacancies(company));

        ScrapeSettings secondPage = getScrapedSettingsInPageOrder().get(1);
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
    void ScrapeCompanyVacancies_MetadataScraperThrowsIOException_ShouldWrapInMetadataScrapeException() {
        IOException cause = new IOException("Connection refused");
        TestCrawler crawler = createCrawler(createMetadataCrawler(settings -> {
            throw cause;
        }, MetadataParams.builder().build()));

        MetadataScrapeException exception = assertThrows(MetadataScrapeException.class,
                () -> crawler.scrapeCompanyVacancies(createCompany()));

        assertSame(cause, exception.getCause());
        assertTrue(scrapedSettings.isEmpty());
    }

    @Test
    void ScrapeCompanyVacancies_MetadataScraperThrowsRuntimeException_ShouldWrapInMetadataScrapeException() {
        TestCrawler crawler = createCrawler(createMetadataCrawler(settings -> {
            throw new IllegalStateException("Browser crashed");
        }, MetadataParams.builder().build()));

        MetadataScrapeException exception = assertThrows(MetadataScrapeException.class,
                () -> crawler.scrapeCompanyVacancies(createCompany()));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void ScrapeCompanyVacancies_ParseMetadataFails_ShouldWrapInMetadataParseException() {
        TestCrawler crawler = createCrawler(new CompanyMetadataCrawler() {
            @Override
            public ScrapeSettings configureScrapeSettings(CompanyDTO company) {
                return ScrapeSettings.builder().build();
            }

            @Override
            public PageScraper configurePageScraper() {
                return settings -> new PageScraperResponse(0, null, Map.of(), Map.of(), "<html></html>");
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
                (company, pageId) -> ScrapeSettings.builder().delay(Duration.ofHours(pageId - 1)).build(),
                createMetadataCrawler(MetadataParams.builder().pagesCount(2).build()));

        List<CompletableFuture<PageScraperResponse>> pages = crawler.scrapeCompanyVacancies(createCompany());
        CompletableFuture<PageScraperResponse> waitingPage = pages.get(1);
        pages.getFirst().orTimeout(TIMEOUT_SEC, TimeUnit.SECONDS).join();
        assertFalse(waitingPage.isDone());

        crawlingProgressService.requestStop();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> waitingPage.get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertInstanceOf(CrawlingStoppedException.class, exception.getCause());
    }

    @Test
    void ScrapeCompanyVacancies_UnsetFields_ShouldFillThemFromCompany() {
        joinPages(createCrawler(null).scrapeCompanyVacancies(createCompany()));

        ScrapeSettings settings = scrapedSettings.getFirst();
        assertEquals(Map.of("page", "1", "city", "Kyiv"), settings.getData());
        assertEquals(Map.of("Accept", "text/html", "User-Agent", "configured"), settings.getHeaders());
        assertEquals(Map.of("session", "configured", "locale", "en"), settings.getCookies());
    }

    @Test
    void ScrapeCompanyVacancies_EmptyMapsSetByCrawler_ShouldKeepThemEmpty() {
        TestCrawler crawler = createCrawler(
                (company, pageId) -> ScrapeSettings.builder().data(Map.of()).headers(Map.of()).cookies(Map.of()).build(),
                null);

        joinPages(crawler.scrapeCompanyVacancies(createCompany()));

        ScrapeSettings settings = scrapedSettings.getFirst();
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

        ScrapeSettings settings = scrapedSettings.getFirst();
        assertEquals(Map.of("Accept", "text/html", "user-agent", "from metadata", "X-Csrf", "token"),
                settings.getHeaders());
        assertEquals(Map.of("session", "from metadata", "locale", "en"), settings.getCookies());
    }

    @Test
    void ScrapeCompanyVacancies_PageScraperThrowsIOException_ShouldFailPageWithVacancyPageScrapeException() {
        IOException cause = new IOException("Connection reset");
        TestCrawler crawler = initCrawler(new TestCrawler((company, pageId) -> ScrapeSettings.builder().build(),
                settings -> {
                    throw cause;
                }, null, List.of(), null));

        CompletableFuture<PageScraperResponse> page = crawler.scrapeCompanyVacancies(createCompany()).getFirst();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> page.get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertInstanceOf(VacancyPageScrapeException.class, exception.getCause());
        assertSame(cause, exception.getCause().getCause());
    }

    @Test
    void ParsePageVacancies_NoCallVacancyDetailsCrawler_ShouldReturnPreviewCopies() {
        List<VacancyDTO> previewVacancies = List.of(createPreviewVacancy("https://example.com/a"),
                createPreviewVacancy("https://example.com/b"));
        TestCrawler crawler = createVacanciesCrawler(previewVacancies, null);

        List<VacancyDTO> vacancies = crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertEquals(2, vacancies.size());
        for (int i = 0; i < vacancies.size(); i++) {
            assertNotSame(previewVacancies.get(i), vacancies.get(i));
            assertEquals(previewVacancies.get(i).getLink(), vacancies.get(i).getLink());
            assertEquals(previewVacancies.get(i).getTitle(), vacancies.get(i).getTitle());
        }
    }

    @Test
    void ParsePageVacancies_VacancyDetailsCrawler_ShouldReplacePreviews() {
        List<VacancyDTO> previewVacancies = List.of(createPreviewVacancy("https://example.com/a"),
                createPreviewVacancy("https://example.com/b"));
        TestCrawler crawler = createVacanciesCrawler(previewVacancies, createVacancyDetailsCrawler(settings ->
                new PageScraperResponse(0, settings.getPageURL(), Map.of(), Map.of(),
                        "Description of " + settings.getPageURL())));

        List<VacancyDTO> vacancies = crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertEquals(List.of("https://example.com/a", "https://example.com/b"),
                vacancies.stream().map(VacancyDTO::getLink).toList());
        assertEquals(List.of("Description of https://example.com/a", "Description of https://example.com/b"),
                vacancies.stream().map(VacancyDTO::getDescription).toList());
        assertEquals("Title of https://example.com/a", vacancies.getFirst().getTitle());
    }

    @Test
    void ParsePageVacancies_ScrapeFails_ShouldKeepPreview() {
        VacancyDTO previewVacancy = createPreviewVacancy("https://example.com/a");
        TestCrawler crawler = createVacanciesCrawler(List.of(previewVacancy), createVacancyDetailsCrawler(settings -> {
            throw new IOException("Connection reset");
        }));

        List<VacancyDTO> vacancies = crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertSame(previewVacancy, vacancies.getFirst());
        assertTrue(getFirstPageLogs().stream()
                .anyMatch(log -> log.level() == CrawlerProgress.LogLevelEnum.ERROR));
    }

    @Test
    void ParsePageVacancies_ParseReturnsNull_ShouldKeepPreview() {
        VacancyDTO previewVacancy = createPreviewVacancy("https://example.com/a");
        TestCrawler crawler = createVacanciesCrawler(List.of(previewVacancy), new VacancyDetailsCrawler() {
            @Override
            public ScrapeSettings configureScrapeSettings(VacancyDTO preview, CompanyDTO company,
                                                        int vacancyOrderOnPage) {
                return ScrapeSettings.builder().pageURL(preview.getLink()).build();
            }

            @Override
            public PageScraper configurePageScraper() {
                return settings -> new PageScraperResponse(0, settings.getPageURL(), Map.of(), Map.of(), "");
            }

            @Override
            public VacancyDTO parseVacancyDetails(VacancyDTO preview, PageScraperResponse response) {
                return null;
            }
        });

        List<VacancyDTO> vacancies = crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertSame(previewVacancy, vacancies.getFirst());
    }

    @Test
    void ParsePageVacancies_StopRequested_ShouldKeepPreviews() {
        List<ScrapeSettings> vacancySettings = new CopyOnWriteArrayList<>();
        VacancyDTO previewVacancy = createPreviewVacancy("https://example.com/a");
        TestCrawler crawler = createVacanciesCrawler(List.of(previewVacancy), createVacancyDetailsCrawler(settings -> {
            vacancySettings.add(settings);
            return new PageScraperResponse(0, settings.getPageURL(), Map.of(), Map.of(), "Description");
        }));
        crawlingProgressService.requestStop();

        List<VacancyDTO> vacancies = crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertSame(previewVacancy, vacancies.getFirst());
        assertTrue(vacancySettings.isEmpty());
    }

    @Test
    void ParsePageVacancies_SeveralVacancies_ShouldPassOrderOnPage() {
        Map<String, Integer> ordersByLink = new ConcurrentHashMap<>();
        VacancyDetailsCrawler orderRecordingCrawler = new VacancyDetailsCrawler() {
            @Override
            public ScrapeSettings configureScrapeSettings(VacancyDTO previewVacancy, CompanyDTO company,
                                                        int vacancyOrderOnPage) {
                ordersByLink.put(previewVacancy.getLink(), vacancyOrderOnPage);
                return ScrapeSettings.builder().pageURL(previewVacancy.getLink()).build();
            }

            @Override
            public PageScraper configurePageScraper() {
                return settings -> new PageScraperResponse(0, settings.getPageURL(), Map.of(), Map.of(), "");
            }

            @Override
            public VacancyDTO parseVacancyDetails(VacancyDTO previewVacancy, PageScraperResponse response) {
                return previewVacancy.toBuilder().build();
            }
        };
        TestCrawler crawler = createVacanciesCrawler(List.of(createPreviewVacancy("https://example.com/a"),
                createPreviewVacancy("https://example.com/b"), createPreviewVacancy("https://example.com/c")),
                orderRecordingCrawler);

        crawler.parsePageVacancies(createVacanciesPage(), createCompany());

        assertEquals(Map.of("https://example.com/a", 1, "https://example.com/b", 2, "https://example.com/c", 3),
                ordersByLink);
    }
}
