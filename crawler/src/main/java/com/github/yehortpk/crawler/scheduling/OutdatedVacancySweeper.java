package com.github.yehortpk.crawler.scheduling;

import com.github.yehortpk.crawler.services.NotifierService;
import com.github.yehortpk.crawler.services.VacancyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Drops the vacancies whose one-week grace period has expired, see
 * {@link VacancyService#markOutdated}, and reports them as removed to {@code vacancies}.
 *
 * <p>Runs at a fixed time of the week rather than a week after the crawler started, so restarts
 * do not keep putting it off. A vacancy therefore leaves between one and two weeks after it
 * disappeared from its site.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutdatedVacancySweeper {
    private final VacancyService vacancyService;
    private final NotifierService notifierService;

    @Scheduled(cron = "${outdated-vacancy.sweep-cron}")
    public void dropExpiredVacancies() {
        List<String> expiredLinks = vacancyService.findExpiredLinks(LocalDateTime.now());
        if (expiredLinks.isEmpty()) {
            return;
        }

        // Reported before they are dropped: if the crawler dies in between, the next sweep reports
        // them again, and removing an already removed link changes nothing for the consumers
        try {
            notifierService.notifyRemovedVacancies(expiredLinks);
        } catch (RuntimeException ex) {
            log.error("Expired vacancies were not reported as removed, the next sweep retries them: {}",
                    ex.getMessage());
            return;
        }

        vacancyService.deleteVacancies(expiredLinks);
        log.info("Dropped {} vacancies whose grace period had expired", expiredLinks.size());
    }
}
