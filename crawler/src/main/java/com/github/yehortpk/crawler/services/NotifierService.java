package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.NewVacancyDTO;
import com.github.yehortpk.crawler.models.RemovedVacancyDTO;
import com.github.yehortpk.crawler.models.VacancyDTO;
import com.github.yehortpk.crawler.models.VacancyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes to the {@code vacancies} topic what a crawling run found: new vacancies, and the links
 * of vacancies dropped for good. Both are keyed by the link, so the changes of one vacancy keep
 * their order.
 *
 * <p>A vacancy is remembered as published only after the broker acknowledged it. If the crawler
 * dies in between, the next run publishes it again, and the consumers skip a link they already
 * know.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotifierService {
    private static final long ACK_TIMEOUT_SEC = 30;

    private final KafkaTemplate<String, VacancyMessage> vacancyKafkaTemplate;
    private final VacancyService vacancyService;

    /**
     * Links already published during the current crawling run. Pages of one company are parsed
     * concurrently against the same snapshot of persisted vacancies, so the same vacancy can be
     * classified as new by more than one page; this keeps it from being published twice.
     */
    private final Set<String> publishedLinks = ConcurrentHashMap.newKeySet();

    private static NewVacancyDTO createNewVacancy(VacancyDTO vacancy, CompanyDTO company) {
        return NewVacancyDTO.builder()
                .link(vacancy.getLink())
                .title(vacancy.getTitle())
                .description(vacancy.getDescription())
                .minSalary(vacancy.getMinSalary())
                .maxSalary(vacancy.getMaxSalary())
                .salaryCurrency(vacancy.getSalaryCurrency())
                .salaryPeriod(vacancy.getSalaryPeriod())
                .isRemote(vacancy.isRemote())
                .parsedAt(vacancy.getCrawledAt())
                .company(new NewVacancyDTO.Company(company.getCompanyId(), company.getTitle(), company.getMainPageURL()))
                .build();
    }

    /**
     * Waits for the broker to acknowledge a sent record.
     *
     * @throws IllegalStateException if it was not acknowledged in time
     */
    private static void awaitAck(CompletableFuture<? extends SendResult<String, ?>> send, String link) {
        try {
            send.get(ACK_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + link, ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("The broker did not acknowledge " + link, ex);
        }
    }

    /**
     * Publishes the new vacancies of a company and remembers each as published once the broker
     * has acknowledged it. Skipped are the vacancies already published in this run, and those
     * published earlier under another company, e.g. one listed on an aggregator as well.
     *
     * <p>A vacancy that could not be published is logged and left unrecorded, so the next run
     * finds it new again.
     *
     * @param newVacancies set of new vacancies
     * @param company the company they were parsed from
     */
    public void notifyNewVacancies(Set<VacancyDTO> newVacancies, CompanyDTO company) {
        List<VacancyDTO> unpublishedVacancies = newVacancies.stream()
                .filter(vacancy -> publishedLinks.add(vacancy.getLink()))
                .toList();
        if (unpublishedVacancies.isEmpty()) {
            return;
        }

        Set<String> earlierPublishedLinks = vacancyService.findPublishedLinks(
                unpublishedVacancies.stream().map(VacancyDTO::getLink).toList());

        // Everything is sent before the first ack is awaited, so the producer can batch
        Map<VacancyDTO, CompletableFuture<SendResult<String, VacancyMessage>>> sends = new LinkedHashMap<>();
        for (VacancyDTO vacancy : unpublishedVacancies) {
            if (!earlierPublishedLinks.contains(vacancy.getLink())) {
                sends.put(vacancy, vacancyKafkaTemplate.sendDefault(
                        vacancy.getLink(), createNewVacancy(vacancy, company)));
            }
        }

        sends.forEach((vacancy, send) -> {
            try {
                awaitAck(send, vacancy.getLink());
                vacancyService.addPublishedVacancy(vacancy.getLink(), company.getCompanyId(), vacancy.getCrawledAt());
            } catch (RuntimeException ex) {
                publishedLinks.remove(vacancy.getLink());
                log.error("Vacancy {} was not published, the next run retries it: {}",
                        vacancy.getLink(), ex.getMessage());
            }
        });
    }

    /**
     * Publishes the links of vacancies that are being dropped for good.
     *
     * @throws IllegalStateException if the broker did not acknowledge every link; the caller
     *                               must then keep the vacancies, so the next sweep reports them again
     */
    public void notifyRemovedVacancies(Collection<String> links) {
        Map<String, CompletableFuture<SendResult<String, VacancyMessage>>> sends = new LinkedHashMap<>();
        for (String link : links) {
            sends.put(link, vacancyKafkaTemplate.sendDefault(link, new RemovedVacancyDTO(link)));
        }
        sends.forEach((link, send) -> awaitAck(send, link));
    }

    /**
     * Clears the published links of the finished run so the next one starts from a clean state.
     */
    public void resetPublishedLinks() {
        publishedLinks.clear();
    }
}
