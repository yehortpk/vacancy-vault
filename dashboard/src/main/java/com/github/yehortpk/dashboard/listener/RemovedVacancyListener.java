package com.github.yehortpk.dashboard.listener;

import com.github.yehortpk.dashboard.services.VacancyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Drops the vacancies the parser reports as gone for good. The message is the bare link.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
@Slf4j
public class RemovedVacancyListener {
    private final VacancyService vacancyService;

    @KafkaListener(topics = {"#{environment['KAFKA_REMOVED_VACANCIES_TOPIC']}"},
            containerFactory = "removedVacancyContainerFactory")
    public void listenRemovedVacanciesTopic(String link) {
        if (vacancyService.removeVacancy(link)) {
            log.info("Removed vacancy {}", link);
        }
    }
}
