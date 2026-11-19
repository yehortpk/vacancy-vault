package com.github.yehortpk.dashboard.listener;

import com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.dashboard.services.VacancyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Stores the vacancies the parser publishes
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class NewVacancyListener {
    private final VacancyService vacancyService;

    @KafkaListener(topics = {"#{environment['KAFKA_NEW_VACANCIES_TOPIC']}"},
            containerFactory = "newVacancyContainerFactory")
    public void listenNewVacanciesTopic(NewVacancyDTO vacancy) {
        vacancyService.addVacancy(vacancy);
    }
}
