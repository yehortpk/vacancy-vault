package com.github.yehortpk.crawler.scheduling;

import com.github.yehortpk.crawler.PostgresContainerTest;
import com.github.yehortpk.crawler.models.RemovedVacancyDTO;
import com.github.yehortpk.crawler.models.VacancyMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The weekly sweep over the migrated {@code crawled_vacancy} table. Kafka is replaced by a
 * template that acknowledges at once, so what would be reported as removed is what is asserted.
 */
@SpringBootTest
class OutdatedVacancySweeperTest extends PostgresContainerTest {
    private static final int COMPANY_ID = 1;

    @Autowired
    private OutdatedVacancySweeper outdatedVacancySweeper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KafkaTemplate<String, VacancyMessage> vacancyKafkaTemplate;

    private void clearData() {
        jdbcTemplate.update("DELETE FROM crawled_vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, crawling_enabled) VALUES (?, 'Acme', true)",
                COMPANY_ID);
        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mock(SendResult.class)));
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

    private List<String> findCrawledLinks() {
        return jdbcTemplate.queryForList("SELECT link FROM crawled_vacancy", String.class);
    }

    @Test
    void DropExpiredVacancies_GracePeriodExpired_ShouldReportTheRemovedLinkAndDropTheVacancy() {
        insertCrawledVacancy("listed", null);
        insertCrawledVacancy("expired", LocalDateTime.now().minusMinutes(1));

        outdatedVacancySweeper.dropExpiredVacancies();

        verify(vacancyKafkaTemplate).sendDefault(createLink("expired"), new RemovedVacancyDTO(createLink("expired")));
        assertThat(findCrawledLinks()).containsExactly(createLink("listed"));
    }

    @Test
    void DropExpiredVacancies_GracePeriodStillRunning_ShouldKeepTheVacancy() {
        insertCrawledVacancy("waiting", LocalDateTime.now().plusDays(3));

        outdatedVacancySweeper.dropExpiredVacancies();

        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), any());
        assertThat(findCrawledLinks()).containsExactly(createLink("waiting"));
    }

    @Test
    void DropExpiredVacancies_RemovedLinkNotAcknowledged_ShouldKeepTheVacancyForTheNextSweep() {
        insertCrawledVacancy("expired", LocalDateTime.now().minusMinutes(1));
        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(new IllegalStateException("Broker is down")));

        outdatedVacancySweeper.dropExpiredVacancies();

        assertThat(findCrawledLinks()).containsExactly(createLink("expired"));
    }
}
