package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.progress.CrawlerProgress;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.progress.CrawlingStatusEnum;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class CrawlingProgressService {
    private String crawlingHash;
    @Getter
    private Map<Integer, CrawlerProgress> crawlers;
    private final ReentrantLock progressLock = new ReentrantLock();

    /**
     * Pages of every company are parsed concurrently, so the totals are accumulated atomically
     * instead of with a plain {@code +=}, which loses updates under concurrent increments.
     */
    private final AtomicInteger crawledVacanciesCnt = new AtomicInteger();
    private final AtomicInteger newVacanciesCnt = new AtomicInteger();
    private final AtomicInteger outdatedVacanciesCnt = new AtomicInteger();

    @Getter
    private volatile CrawlingStatusEnum status;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    /**
     * Completed by a stop request, so pages waiting out their stagger delay are released at once.
     */
    @Getter
    private volatile CompletableFuture<Void> stopSignal;

    public CrawlingProgressService() {
        resetProgress();
        this.status = CrawlingStatusEnum.IDLE;
    }

    public void init() {
        progressLock.lock();
        try {
            resetProgress();
            this.status = CrawlingStatusEnum.RUNNING;
            this.startedAt = LocalDateTime.now();
        } finally {
            progressLock.unlock();
        }
    }

    private void resetProgress() {
        this.crawlingHash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.crawledVacanciesCnt.set(0);
        this.newVacanciesCnt.set(0);
        this.outdatedVacanciesCnt.set(0);
        this.startedAt = null;
        this.finishedAt = null;
        this.stopSignal = new CompletableFuture<>();

        this.crawlers = new ConcurrentHashMap<>();
    }

    /**
     * Asks the running crawling to stop. Steps already in flight finish, every later one is
     * skipped; the status turns {@link CrawlingStatusEnum#STOPPED} once they are done.
     * @return false if crawling is not running, or is already stopping
     */
    public boolean requestStop() {
        progressLock.lock();
        try {
            if (status != CrawlingStatusEnum.RUNNING) {
                return false;
            }
            this.status = CrawlingStatusEnum.STOPPING;
            this.stopSignal.complete(null);
            return true;
        } finally {
            progressLock.unlock();
        }
    }

    public boolean isStopRequested() {
        return status == CrawlingStatusEnum.STOPPING;
    }

    public void markCompleted() {
        progressLock.lock();
        try {
            this.status = status == CrawlingStatusEnum.STOPPING ? CrawlingStatusEnum.STOPPED : CrawlingStatusEnum.FINISHED;
            this.finishedAt = LocalDateTime.now();
        } finally {
            progressLock.unlock();
        }
    }

    public void markFailed() {
        progressLock.lock();
        try {
            this.status = CrawlingStatusEnum.FAILED;
            this.finishedAt = LocalDateTime.now();
        } finally {
            progressLock.unlock();
        }
    }

    public CrawlerProgress addCrawlerProgress(int id, String title) {
        CrawlerProgress newCrawlerProgress = new CrawlerProgress(id, title);
        CrawlerProgress existing = crawlers.putIfAbsent(id, newCrawlerProgress);
        if (existing != null) {
            throw new RuntimeException("Progress bar with this id is already exist");
        }
        return newCrawlerProgress;
    }

    public int getCrawledVacanciesCnt() {
        return crawledVacanciesCnt.get();
    }

    public int getNewVacanciesCnt() {
        return newVacanciesCnt.get();
    }

    public int getOutdatedVacanciesCnt() {
        return outdatedVacanciesCnt.get();
    }

    public void addCrawledVacancies(int crawledVacancies) {
        crawledVacanciesCnt.addAndGet(crawledVacancies);
    }

    public void addNewVacancies(int newVacancies) {
        newVacanciesCnt.addAndGet(newVacancies);
    }

    public void addOutdatedVacancies(int outdatedVacancies) {
        outdatedVacanciesCnt.addAndGet(outdatedVacancies);
    }

    public CrawlingProgressDTO getProgress() {
        List<CrawlerProgress> crawlers = new ArrayList<>(this.crawlers.values());
        crawlers.sort(Comparator.comparingInt(CrawlerProgress::getId));

        return CrawlingProgressDTO.builder()
                .crawlingHash(crawlingHash)
                .crawlers(crawlers)
                .status(status)
                .finished(status == CrawlingStatusEnum.FINISHED
                        || status == CrawlingStatusEnum.STOPPED
                        || status == CrawlingStatusEnum.FAILED)
                .crawledVacanciesTotalCount(crawledVacanciesCnt.get())
                .newVacanciesTotalCount(newVacanciesCnt.get())
                .outdatedVacanciesTotalCount(outdatedVacanciesCnt.get())
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();
    }
}
