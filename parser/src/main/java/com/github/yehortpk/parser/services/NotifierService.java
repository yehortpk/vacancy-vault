package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.models.VacancyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class sends vacancies notification for the router service
 */
@Service
@RequiredArgsConstructor
public class NotifierService {
    private final KafkaTemplate<String, VacancyDTO> routerService;

    /**
     * Links already published during the current parsing run. Pages of one company are parsed
     * concurrently against the same snapshot of persisted vacancies, so the same vacancy can be
     * classified as new by more than one page; this keeps it from being published twice.
     */
    private final Set<String> publishedLinks = ConcurrentHashMap.newKeySet();

    /**
     * Notify router service about new vacancies, skipping those already published in this run
     * @param newVacancies set of new vacancies
     */
    public void notifyNewVacancies(Set<VacancyDTO> newVacancies) {
        for (VacancyDTO newVacancy : newVacancies) {
            if (publishedLinks.add(newVacancy.getLink())) {
                routerService.send(routerService.getDefaultTopic(), newVacancy.getLink(), newVacancy);
            }
        }
    }

    /**
     * Clears the published links of the finished run so the next one starts from a clean state.
     */
    public void resetPublishedLinks() {
        publishedLinks.clear();
    }
}
