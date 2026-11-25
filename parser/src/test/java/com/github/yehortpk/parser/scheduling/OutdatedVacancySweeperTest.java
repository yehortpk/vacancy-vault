package com.github.yehortpk.parser.scheduling;

import com.github.yehortpk.parser.PostgresContainerTest;
import com.github.yehortpk.parser.models.RemovedVacancyDTO;
import com.github.yehortpk.parser.models.VacancyMessage;
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
 * The weekly sweep over the migrated {@code parsed_vacancy} table. Kafka is replaced by a
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
        jdbcTemplate.update("DELETE FROM parsed_vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, parsing_enabled) VALUES (?, 'Acme', true)",
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

    private void insertParsedVacancy(String path, LocalDateTime deleteAt) {
        jdbcTemplate.update("INSERT INTO parsed_vacancy (link, company_id, parsed_at, delete_at)"
                + " VALUES (?, ?, now(), ?)", createLink(path), COMPANY_ID, deleteAt);
    }

    private List<String> findParsedLinks() {
        return jdbcTemplate.queryForList("SELECT link FROM parsed_vacancy", String.class);
    }

    @Test
    void DropExpiredVacancies_GracePeriodExpired_ShouldReportTheRemovedLinkAndDropTheVacancy() {
        insertParsedVacancy("listed", null);
        insertParsedVacancy("expired", LocalDateTime.now().minusMinutes(1));

        outdatedVacancySweeper.dropExpiredVacancies();

        verify(vacancyKafkaTemplate).sendDefault(createLink("expired"), new RemovedVacancyDTO(createLink("expired")));
        assertThat(findParsedLinks()).containsExactly(createLink("listed"));
    }

    @Test
    void DropExpiredVacancies_GracePeriodStillRunning_ShouldKeepTheVacancy() {
        insertParsedVacancy("waiting", LocalDateTime.now().plusDays(3));

        outdatedVacancySweeper.dropExpiredVacancies();

        verify(vacancyKafkaTemplate, never()).sendDefault(anyString(), any());
        assertThat(findParsedLinks()).containsExactly(createLink("waiting"));
    }

    @Test
    void DropExpiredVacancies_RemovedLinkNotAcknowledged_ShouldKeepTheVacancyForTheNextSweep() {
        insertParsedVacancy("expired", LocalDateTime.now().minusMinutes(1));
        when(vacancyKafkaTemplate.sendDefault(anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(new IllegalStateException("Broker is down")));

        outdatedVacancySweeper.dropExpiredVacancies();

        assertThat(findParsedLinks()).containsExactly(createLink("expired"));
    }
}
