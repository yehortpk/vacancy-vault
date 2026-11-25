package com.github.yehortpk.router.scheduling;

import com.github.yehortpk.router.repositories.NotifiedVacancyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Forgets the links the {@code vacancies} topic can no longer replay.
 *
 * <p>{@code notified_vacancy} only has to remember a link for as long as its message can come
 * back: once the broker has dropped the message, nothing can deliver it a second time. So the
 * retention here must not be shorter than the topic's, and the table stays as small as the
 * topic.
 */
@Component
@Slf4j
public class NotifiedVacancySweeper {
    private final NotifiedVacancyRepository notifiedVacancyRepository;
    private final Duration retention;

    public NotifiedVacancySweeper(NotifiedVacancyRepository notifiedVacancyRepository,
                                  @Value("${notified-vacancy.retention}") Duration retention) {
        this.notifiedVacancyRepository = notifiedVacancyRepository;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${notified-vacancy.sweep-interval}",
            initialDelayString = "${notified-vacancy.sweep-interval}")
    public void deleteExpiredLinks() {
        int deletedLinks = notifiedVacancyRepository.deleteNotifiedBefore(LocalDateTime.now().minus(retention));
        if (deletedLinks > 0) {
            log.info("Forgot {} notified vacancies older than {}", deletedLinks, retention);
        }
    }
}
