package com.github.yehortpk.dashboard.listener;

import com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.dashboard.models.vacancy.RemovedVacancyDTO;
import com.github.yehortpk.dashboard.services.VacancyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies what the crawler publishes to the stored vacancies: stores the new ones and drops the
 * ones gone for good. Both arrive on one topic keyed by the link, so a vacancy removed and then
 * listed again is applied in that order.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@KafkaListener(topics = {"#{environment['KAFKA_VACANCIES_TOPIC']}"},
        containerFactory = "vacancyContainerFactory")
@RequiredArgsConstructor
@Slf4j
public class VacancyListener {
    private final VacancyService vacancyService;

    @KafkaHandler
    public void storeVacancy(NewVacancyDTO vacancy) {
        vacancyService.addVacancy(vacancy);
    }

    @KafkaHandler
    public void dropVacancy(RemovedVacancyDTO removedVacancy) {
        if (vacancyService.removeVacancy(removedVacancy.link())) {
            log.info("Removed vacancy {}", removedVacancy.link());
        }
    }
}
