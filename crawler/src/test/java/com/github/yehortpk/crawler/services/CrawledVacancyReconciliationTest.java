package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.PostgresContainerTest;
import com.github.yehortpk.crawler.crawler.CompanyCrawler;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.NewVacancyDTO;
import com.github.yehortpk.crawler.models.CrawledVacancy;
import com.github.yehortpk.crawler.models.RemovedVacancyDTO;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.models.VacancyMessage;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.repositories.CrawledVacancyRepository;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A crawling run against the migrated {@code crawled_vacancy} table: which vacancies it finds new
 * and publishes, and which it marks outdated. Dropping expired ones is the sweeper's, see
 * {@code OutdatedVacancySweeperTest}.
 *
 * <p>The site is replaced by a crawler that returns one page of fixed vacancies, and Kafka by
 * a template that acknowledges at once, so what would be published is what is asserted. Each
 * test runs its own {@link CrawlerRunnerService}, built over the real vacancy service.
 */
@SpringBootTest
class CrawledVacancyReconciliationTest extends PostgresContainerTest {
    private static final int TIMEOUT_MS = 5000;
    private static final int COMPANY_ID = 1;
    private static final String CRAWLER_BEAN = "acme-company";

    @Autowired
    private VacancyService vacancyService;

    @Autowired
    private CrawledVacancyRepository crawledVacancyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KafkaTemplate<String, VacancyMessage> vacancyKafkaTemplate;

    private final CompanyService companyService = mock(CompanyService.class);
    private final CrawlingHistoryService crawlingHistoryService = mock(CrawlingHistoryService.class);
    private final CompanyCrawler crawler = mock(CompanyCrawler.class);

    private void clearData() {
        jdbcTemplate.update("DELETE FROM crawled_vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, main_page_url, crawling_enabled)"
                + " VALUES (?, 'Acme', 'https://acme.example', true)", COMPANY_ID);

        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mock(SendResult.class)));

        when(companyService.getCompaniesList()).thenReturn(List.of(CompanyDTO.builder()
                .companyId(COMPANY_ID)
                .beanClass(CRAWLER_BEAN)
                .title("Acme")
                .mainPageURL("https://acme.example")
                .build()));
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private static String createLink(String path) {
        return "https://acme.example/" + path;
    }

    private void insertCrawledVacancy(String path, LocalDateTime deleteAt) {
        jdbcTemplate.update("INSERT INTO crawled_vacancy (link, company_id, crawled_at, delete_at)"
                + " VALUES (?, ?, now(), ?)", createLink(path), COMPANY_ID, deleteAt);
    }

    private CrawledVacancy findCrawledVacancy(String path) {
        return crawledVacancyRepository.findByCompanyId(COMPANY_ID).stream()
                .filter(vacancy -> vacancy.getLink().equals(createLink(path)))
                .findFirst().orElseThrow();
    }

    private List<String> findCrawledLinks() {
        return crawledVacancyRepository.findByCompanyId(COMPANY_ID).stream().map(CrawledVacancy::getLink).toList();
    }

    /** Runs one crawling of the company whose site lists exactly these vacancies, and waits for it. */
    private CrawlingProgressDTO runCrawling(String... listedPaths) throws Exception {
        PageScraperResponse page = new PageScraperResponse(1, createLink(""), Map.of(), Map.of(), "");
        when(crawler.scrapeCompanyVacancies(any())).thenReturn(List.of(CompletableFuture.completedFuture(page)));
        when(crawler.parseVacancies(eq(page), any())).thenReturn(Arrays.stream(listedPaths)
                .map(path -> VacancyDTO.builder().link(createLink(path)).title("Java Developer").build())
                .toList());

        NotifierService notifierService =
                new NotifierService(vacancyKafkaTemplate, vacancyService);
        CrawlerRunnerService crawlerRunnerService = new CrawlerRunnerService(Map.of(CRAWLER_BEAN, crawler), companyService,
                notifierService, new CrawlingProgressService(), crawlingHistoryService, vacancyService);

        crawlerRunnerService.runCrawling();

        ArgumentCaptor<CrawlingProgressDTO> progress = ArgumentCaptor.forClass(CrawlingProgressDTO.class);
        verify(crawlingHistoryService, timeout(TIMEOUT_MS)).saveProgress(progress.capture());
        return progress.getValue();
    }

    private List<NewVacancyDTO> capturePublishedVacancies(int count) {
        ArgumentCaptor<NewVacancyDTO> vacancies = ArgumentCaptor.forClass(NewVacancyDTO.class);
        verify(vacancyKafkaTemplate, times(count)).sendDefault(anyString(), vacancies.capture());
        return vacancies.getAllValues();
    }

    @Test
    void RunCrawling_LinkMissingFromCrawledVacancy_ShouldPublishAndRecordIt() throws Exception {
        insertCrawledVacancy("known", null);

        CrawlingProgressDTO progress = runCrawling("known", "fresh");

        NewVacancyDTO published = capturePublishedVacancies(1).getFirst();
        assertThat(published.getLink()).isEqualTo(createLink("fresh"));
        assertThat(published.getParsedAt()).isNotNull();
        assertThat(published.getCompany().getId()).isEqualTo(COMPANY_ID);
        assertThat(published.getCompany().getTitle()).isEqualTo("Acme");
        assertThat(published.getCompany().getUrl()).isEqualTo("https://acme.example");
        verify(vacancyKafkaTemplate).sendDefault(createLink("fresh"), published);

        assertThat(findCrawledLinks()).containsExactlyInAnyOrder(createLink("known"), createLink("fresh"));
        assertThat(progress.getNewVacanciesTotalCount()).isEqualTo(1);
    }

    @Test
    void RunCrawling_SecondRunOverTheSameSite_ShouldPublishNothing() throws Exception {
        runCrawling("first", "second");
        capturePublishedVacancies(2);

        runCrawling("first", "second");

        // Still the two sends of the first run
        capturePublishedVacancies(2);
        assertThat(findCrawledLinks()).hasSize(2);
    }

    @Test
    void RunCrawling_BrokerDoesNotAcknowledge_ShouldNotRecordTheVacancy() throws Exception {
        // Left unrecorded, the next run finds it new again and retries
        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(new IllegalStateException("Broker is down")));

        runCrawling("fresh");

        assertThat(findCrawledLinks()).isEmpty();
    }

    @Test
    void RunCrawling_PersistedLinkGoneFromTheSite_ShouldStartItsGracePeriod() throws Exception {
        insertCrawledVacancy("listed", null);
        insertCrawledVacancy("gone", null);

        CrawlingProgressDTO progress = runCrawling("listed");

        assertThat(findCrawledVacancy("listed").getDeleteAt()).isNull();
        assertThat(findCrawledVacancy("gone").getDeleteAt())
                .isBetween(LocalDateTime.now().plusDays(6), LocalDateTime.now().plusDays(8));
        assertThat(progress.getOutdatedVacanciesTotalCount()).isEqualTo(1);
        // Nobody outside the crawler hears about it while the grace period lasts
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunCrawling_OutdatedLinkListedAgain_ShouldLiftItsDeletionMarker() throws Exception {
        insertCrawledVacancy("back", LocalDateTime.now().minusDays(1));

        runCrawling("back");

        assertThat(findCrawledVacancy("back").getDeleteAt()).isNull();
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(NewVacancyDTO.class));
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunCrawling_GracePeriodStillRunning_ShouldKeepTheVacancy() throws Exception {
        LocalDateTime deleteAt = LocalDateTime.now().plusDays(3).withNano(0);
        insertCrawledVacancy("listed", null);
        insertCrawledVacancy("waiting", deleteAt);

        CrawlingProgressDTO progress = runCrawling("listed");

        assertThat(findCrawledVacancy("waiting").getDeleteAt()).isEqualTo(deleteAt);
        assertThat(progress.getOutdatedVacanciesTotalCount()).isZero();
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunCrawling_GracePeriodExpired_ShouldLeaveTheVacancyToTheSweeper() throws Exception {
        insertCrawledVacancy("listed", null);
        insertCrawledVacancy("expired", LocalDateTime.now().minusMinutes(1));

        runCrawling("listed");

        assertThat(findCrawledLinks()).containsExactlyInAnyOrder(createLink("listed"), createLink("expired"));
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunCrawling_LinkAlreadyPublishedUnderAnotherCompany_ShouldNotPublishItAgain() throws Exception {
        // E.g. a vacancy listed on its company's site and on an aggregator
        jdbcTemplate.update("INSERT INTO company (company_id, title, crawling_enabled) VALUES (2, 'Aggregator', false)");
        jdbcTemplate.update("INSERT INTO crawled_vacancy (link, company_id, crawled_at) VALUES (?, 2, now())",
                createLink("shared"));

        runCrawling("shared");

        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(NewVacancyDTO.class));
        assertThat(crawledVacancyRepository.count()).isEqualTo(1);
    }
}
