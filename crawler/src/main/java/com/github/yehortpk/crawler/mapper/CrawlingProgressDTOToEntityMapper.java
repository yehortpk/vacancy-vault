package com.github.yehortpk.crawler.mapper;

import com.github.yehortpk.crawler.models.history.CrawlerHistory;
import com.github.yehortpk.crawler.models.history.CrawlerPageHistory;
import com.github.yehortpk.crawler.models.history.CrawlerPageLog;
import com.github.yehortpk.crawler.models.history.CrawlingHistory;
import com.github.yehortpk.crawler.progress.CrawlerProgress;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the live progress of a run into the document stored for it. The progress objects are
 * still being written by the run's threads, so everything is copied rather than referenced.
 */
@Component
public class CrawlingProgressDTOToEntityMapper {

    private CrawlerPageLog mapPageLog(int logId, CrawlerProgress.PageProgress.PageLog log) {
        return new CrawlerPageLog(logId, log.level(), log.timestamp(), log.message());
    }

    private CrawlerPageHistory mapPage(CrawlerProgress.PageProgress page) {
        // The list is synchronized, and copying it is the one compound action that needs the lock
        List<CrawlerProgress.PageProgress.PageLog> logs;
        synchronized (page.getLogs()) {
            logs = new ArrayList<>(page.getLogs());
        }

        List<CrawlerPageLog> pageLogs = new ArrayList<>();
        for (int i = 0; i < logs.size(); i++) {
            pageLogs.add(mapPageLog(i + 1, logs.get(i)));
        }
        return new CrawlerPageHistory(page.getId(), page.getStatus(), page.getCrawledVacanciesCnt(), pageLogs);
    }

    private CrawlerHistory mapCrawler(CrawlerProgress crawler) {
        return new CrawlerHistory(
                crawler.getId(),
                crawler.getTitle(),
                crawler.getTotalPages(),
                crawler.getStatus(),
                crawler.getCrawledVacanciesCnt(),
                crawler.getNewVacanciesCnt(),
                crawler.getOutdatedVacanciesCnt(),
                crawler.getPages().stream().map(this::mapPage).toList());
    }

    public CrawlingHistory mapToEntity(CrawlingProgressDTO progress) {
        CrawlingHistory history = new CrawlingHistory();
        history.setCrawlingHash(progress.getCrawlingHash());
        history.setCrawledVacancies(progress.getCrawledVacanciesTotalCount());
        history.setNewVacancies(progress.getNewVacanciesTotalCount());
        history.setOutdatedVacancies(progress.getOutdatedVacanciesTotalCount());
        history.setStatus(progress.getStatus());
        history.setFinished(progress.isFinished());
        history.setCrawlers(progress.getCrawlers().stream().map(this::mapCrawler).toList());
        history.setStartedAt(progress.getStartedAt());
        history.setFinishedAt(progress.getFinishedAt());
        return history;
    }
}
