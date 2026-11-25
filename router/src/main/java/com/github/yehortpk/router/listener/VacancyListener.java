package com.github.yehortpk.router.listener;

import com.github.yehortpk.router.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.router.models.vacancy.RemovedVacancyDTO;
import com.github.yehortpk.router.repositories.NotifiedVacancyRepository;
import com.github.yehortpk.router.services.NotifierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Notifies the matching destinations of every new vacancy the parser publishes, once per link.
 * Removed vacancies share the topic and are skipped.
 *
 * <p>Kafka delivers at least once and the parser republishes after a crash, so the link is
 * recorded first and a link that was already recorded is skipped.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@KafkaListener(topics = {"#{environment['KAFKA_VACANCIES_TOPIC']}"},
        containerFactory = "vacancyContainerFactory")
@RequiredArgsConstructor
@Slf4j
public class VacancyListener {
    private final NotifiedVacancyRepository notifiedVacancyRepository;
    private final NotifierService notifierService;

    @KafkaHandler
    public void notifyAboutVacancy(NewVacancyDTO vacancy) {
        boolean isFirstDelivery = notifiedVacancyRepository.insertIfAbsent(vacancy.getLink(), LocalDateTime.now()) > 0;

        if (isFirstDelivery) {
            notifierService.notifyUsers(vacancy);
        } else {
            log.debug("Vacancy {} was already notified about, skipping", vacancy.getLink());
        }
    }

    /** Nothing to undo: a chat already notified about the vacancy keeps its message. */
    @KafkaHandler
    public void skipRemovedVacancy(RemovedVacancyDTO removedVacancy) {
        log.debug("Vacancy {} was removed, nothing to notify", removedVacancy.link());
    }
}
