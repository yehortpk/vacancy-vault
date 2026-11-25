package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.PostgresContainerTest;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.NewVacancyDTO;
import com.github.yehortpk.parser.models.PageDTO;
import com.github.yehortpk.parser.models.ParsedVacancy;
import com.github.yehortpk.parser.models.RemovedVacancyDTO;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.models.VacancyMessage;
import com.github.yehortpk.parser.parser.SiteParser;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.repositories.ParsedVacancyRepository;
import com.github.yehortpk.parser.scrapper.site.SiteScrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A parsing run against the migrated {@code parsed_vacancy} table: which vacancies it finds new
 * and publishes, and which it marks outdated. Dropping expired ones is the sweeper's, see
 * {@code OutdatedVacancySweeperTest}.
 *
 * <p>The site is replaced by a crawler that returns one page of fixed vacancies, and Kafka by
 * a template that acknowledges at once, so what would be published is what is asserted. Each
 * test runs its own {@link ParserRunnerService}, built over the real vacancy service.
 */
@SpringBootTest
class ParsedVacancyReconciliationTest extends PostgresContainerTest {
    private static final int TIMEOUT_MS = 5000;
    private static final int COMPANY_ID = 1;
    private static final String CRAWLER_BEAN = "acme-company";

    @Autowired
    private VacancyService vacancyService;

    @Autowired
    private ParsedVacancyRepository parsedVacancyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KafkaTemplate<String, VacancyMessage> vacancyKafkaTemplate;

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final ParsingHistoryService parsingHistoryService = mock(ParsingHistoryService.class);
    /** One bean is both the scrapper and the parser of a company, as the real crawlers are. */
    private final SiteScrapper crawler = mock(SiteScrapper.class, withSettings().extraInterfaces(SiteParser.class));

    private void clearData() {
        jdbcTemplate.update("DELETE FROM parsed_vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, main_page_url, parsing_enabled)"
                + " VALUES (?, 'Acme', 'https://acme.example', true)", COMPANY_ID);

        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mock(SendResult.class)));

        when(applicationContext.getBean(CRAWLER_BEAN)).thenReturn(crawler);
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

    private void insertParsedVacancy(String path, LocalDateTime deleteAt) {
        jdbcTemplate.update("INSERT INTO parsed_vacancy (link, company_id, parsed_at, delete_at)"
                + " VALUES (?, ?, now(), ?)", createLink(path), COMPANY_ID, deleteAt);
    }

    private ParsedVacancy findParsedVacancy(String path) {
        return parsedVacancyRepository.findByCompanyId(COMPANY_ID).stream()
                .filter(vacancy -> vacancy.getLink().equals(createLink(path)))
                .findFirst().orElseThrow();
    }

    private List<String> findParsedLinks() {
        return parsedVacancyRepository.findByCompanyId(COMPANY_ID).stream().map(ParsedVacancy::getLink).toList();
    }

    /** Runs one parsing of the company whose site lists exactly these vacancies, and waits for it. */
    private ParsingProgressDTO runParsing(String... listedPaths) throws Exception {
        PageDTO page = new PageDTO();
        page.setPageID(1);
        when(crawler.scrapCompanyVacancies(any())).thenReturn(List.of(CompletableFuture.completedFuture(page)));
        when(((SiteParser) crawler).parseVacancies(page)).thenReturn(Arrays.stream(listedPaths)
                .map(path -> VacancyDTO.builder().link(createLink(path)).title("Java Developer").build())
                .toList());

        NotifierService notifierService =
                new NotifierService(vacancyKafkaTemplate, vacancyService);
        ParserRunnerService parserRunnerService = new ParserRunnerService(applicationContext, companyService,
                notifierService, new ParsingProgressService(), parsingHistoryService, vacancyService);

        parserRunnerService.runParsing();

        ArgumentCaptor<ParsingProgressDTO> progress = ArgumentCaptor.forClass(ParsingProgressDTO.class);
        verify(parsingHistoryService, timeout(TIMEOUT_MS)).saveProgress(progress.capture());
        return progress.getValue();
    }

    private List<NewVacancyDTO> capturePublishedVacancies(int count) {
        ArgumentCaptor<NewVacancyDTO> vacancies = ArgumentCaptor.forClass(NewVacancyDTO.class);
        verify(vacancyKafkaTemplate, times(count)).sendDefault(anyString(), vacancies.capture());
        return vacancies.getAllValues();
    }

    @Test
    void RunParsing_LinkMissingFromParsedVacancy_ShouldPublishAndRecordIt() throws Exception {
        insertParsedVacancy("known", null);

        ParsingProgressDTO progress = runParsing("known", "fresh");

        NewVacancyDTO published = capturePublishedVacancies(1).getFirst();
        assertThat(published.getLink()).isEqualTo(createLink("fresh"));
        assertThat(published.getParsedAt()).isNotNull();
        assertThat(published.getCompany().getId()).isEqualTo(COMPANY_ID);
        assertThat(published.getCompany().getTitle()).isEqualTo("Acme");
        assertThat(published.getCompany().getUrl()).isEqualTo("https://acme.example");
        verify(vacancyKafkaTemplate).sendDefault(createLink("fresh"), published);

        assertThat(findParsedLinks()).containsExactlyInAnyOrder(createLink("known"), createLink("fresh"));
        assertThat(progress.getNewVacanciesTotalCount()).isEqualTo(1);
    }

    @Test
    void RunParsing_SecondRunOverTheSameSite_ShouldPublishNothing() throws Exception {
        runParsing("first", "second");
        capturePublishedVacancies(2);

        runParsing("first", "second");

        // Still the two sends of the first run
        capturePublishedVacancies(2);
        assertThat(findParsedLinks()).hasSize(2);
    }

    @Test
    void RunParsing_BrokerDoesNotAcknowledge_ShouldNotRecordTheVacancy() throws Exception {
        // Left unrecorded, the next run finds it new again and retries
        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(new IllegalStateException("Broker is down")));

        runParsing("fresh");

        assertThat(findParsedLinks()).isEmpty();
    }

    @Test
    void RunParsing_PersistedLinkGoneFromTheSite_ShouldStartItsGracePeriod() throws Exception {
        insertParsedVacancy("listed", null);
        insertParsedVacancy("gone", null);

        ParsingProgressDTO progress = runParsing("listed");

        assertThat(findParsedVacancy("listed").getDeleteAt()).isNull();
        assertThat(findParsedVacancy("gone").getDeleteAt())
                .isBetween(LocalDateTime.now().plusDays(6), LocalDateTime.now().plusDays(8));
        assertThat(progress.getOutdatedVacanciesTotalCount()).isEqualTo(1);
        // Nobody outside the parser hears about it while the grace period lasts
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunParsing_OutdatedLinkListedAgain_ShouldLiftItsDeletionMarker() throws Exception {
        insertParsedVacancy("back", LocalDateTime.now().minusDays(1));

        runParsing("back");

        assertThat(findParsedVacancy("back").getDeleteAt()).isNull();
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(NewVacancyDTO.class));
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunParsing_GracePeriodStillRunning_ShouldKeepTheVacancy() throws Exception {
        LocalDateTime deleteAt = LocalDateTime.now().plusDays(3).withNano(0);
        insertParsedVacancy("listed", null);
        insertParsedVacancy("waiting", deleteAt);

        ParsingProgressDTO progress = runParsing("listed");

        assertThat(findParsedVacancy("waiting").getDeleteAt()).isEqualTo(deleteAt);
        assertThat(progress.getOutdatedVacanciesTotalCount()).isZero();
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunParsing_GracePeriodExpired_ShouldLeaveTheVacancyToTheSweeper() throws Exception {
        insertParsedVacancy("listed", null);
        insertParsedVacancy("expired", LocalDateTime.now().minusMinutes(1));

        runParsing("listed");

        assertThat(findParsedLinks()).containsExactlyInAnyOrder(createLink("listed"), createLink("expired"));
        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(RemovedVacancyDTO.class));
    }

    @Test
    void RunParsing_LinkAlreadyPublishedUnderAnotherCompany_ShouldNotPublishItAgain() throws Exception {
        // E.g. a vacancy listed on its company's site and on an aggregator
        jdbcTemplate.update("INSERT INTO company (company_id, title, parsing_enabled) VALUES (2, 'Aggregator', false)");
        jdbcTemplate.update("INSERT INTO parsed_vacancy (link, company_id, parsed_at) VALUES (?, 2, now())",
                createLink("shared"));

        runParsing("shared");

        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), isA(NewVacancyDTO.class));
        assertThat(parsedVacancyRepository.count()).isEqualTo(1);
    }
}
