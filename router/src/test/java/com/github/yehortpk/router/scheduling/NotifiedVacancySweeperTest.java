package com.github.yehortpk.router.scheduling;

import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.vacancy.NotifiedVacancy;
import com.github.yehortpk.router.repositories.NotifiedVacancyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweeper over the migrated {@code notified_vacancy} table. Each test builds its own
 * sweeper with the retention under test and runs it by hand.
 */
@SpringBootTest
class NotifiedVacancySweeperTest extends PostgresContainerTest {
    private static final Duration RETENTION = Duration.ofDays(8);

    @Autowired
    private NotifiedVacancyRepository notifiedVacancyRepository;

    @BeforeEach
    void clearBefore() {
        notifiedVacancyRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        notifiedVacancyRepository.deleteAll();
    }

    private void insertNotifiedVacancy(String link, Duration age) {
        notifiedVacancyRepository.save(new NotifiedVacancy(link, LocalDateTime.now().minus(age)));
    }

    @Test
    void DeleteExpiredLinks_LinkOlderThanRetention_ShouldDeleteIt() {
        insertNotifiedVacancy("https://acme.example/old", RETENTION.plusHours(1));
        insertNotifiedVacancy("https://acme.example/recent", RETENTION.minusHours(1));

        new NotifiedVacancySweeper(notifiedVacancyRepository, RETENTION).deleteExpiredLinks();

        assertThat(notifiedVacancyRepository.findAll()).extracting(NotifiedVacancy::getLink)
                .containsExactly("https://acme.example/recent");
    }

    @Test
    void DeleteExpiredLinks_NothingOlderThanRetention_ShouldKeepEveryLink() {
        insertNotifiedVacancy("https://acme.example/1", Duration.ofMinutes(5));
        insertNotifiedVacancy("https://acme.example/2", Duration.ofDays(7));

        new NotifiedVacancySweeper(notifiedVacancyRepository, RETENTION).deleteExpiredLinks();

        assertThat(notifiedVacancyRepository.count()).isEqualTo(2);
    }

    @Test
    void InsertIfAbsent_LinkAlreadyRecorded_ShouldKeepTheFirstTimestamp() {
        // Otherwise every redelivery would push the link's expiry further out
        LocalDateTime firstNotifiedAt = LocalDateTime.now().minusDays(3).withNano(0);
        notifiedVacancyRepository.insertIfAbsent("https://acme.example/1", firstNotifiedAt);

        int inserted = notifiedVacancyRepository.insertIfAbsent("https://acme.example/1", LocalDateTime.now());

        assertThat(inserted).isZero();
        assertThat(notifiedVacancyRepository.findById("https://acme.example/1").orElseThrow().getNotifiedAt())
                .isEqualTo(firstNotifiedAt);
    }
}
