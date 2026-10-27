package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.progress.ParsingStatusEnum;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ParsingProgressService {
    private String parsingHash;
    @Getter
    private Map<Integer, ParserProgress> parsers;
    private final ReentrantLock progressLock = new ReentrantLock();

    /**
     * Pages of every company are parsed concurrently, so the totals are accumulated atomically
     * instead of with a plain {@code +=}, which loses updates under concurrent increments.
     */
    private final AtomicInteger parsedVacanciesCnt = new AtomicInteger();
    private final AtomicInteger newVacanciesCnt = new AtomicInteger();
    private final AtomicInteger outdatedVacanciesCnt = new AtomicInteger();

    @Getter
    private volatile ParsingStatusEnum status;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;

    public ParsingProgressService() {
        resetProgress();
        this.status = ParsingStatusEnum.IDLE;
    }

    public void init() {
        progressLock.lock();
        try {
            resetProgress();
            this.status = ParsingStatusEnum.RUNNING;
            this.startedAt = LocalDateTime.now();
        } finally {
            progressLock.unlock();
        }
    }

    private void resetProgress() {
        this.parsingHash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.parsedVacanciesCnt.set(0);
        this.newVacanciesCnt.set(0);
        this.outdatedVacanciesCnt.set(0);
        this.startedAt = null;
        this.finishedAt = null;

        this.parsers = new ConcurrentHashMap<>();
    }

    /**
     * Asks the running parsing to stop. Steps already in flight finish, every later one is
     * skipped; the status turns {@link ParsingStatusEnum#STOPPED} once they are done.
     * @return false if parsing is not running, or is already stopping
     */
    public boolean requestStop() {
        progressLock.lock();
        try {
            if (status != ParsingStatusEnum.RUNNING) {
                return false;
            }
            this.status = ParsingStatusEnum.STOPPING;
            return true;
        } finally {
            progressLock.unlock();
        }
    }

    public boolean isStopRequested() {
        return status == ParsingStatusEnum.STOPPING;
    }

    public void markCompleted() {
        progressLock.lock();
        try {
            this.status = status == ParsingStatusEnum.STOPPING ? ParsingStatusEnum.STOPPED : ParsingStatusEnum.FINISHED;
            this.finishedAt = LocalDateTime.now();
        } finally {
            progressLock.unlock();
        }
    }

    public ParserProgress addParserProgress(int id, String title) {
        ParserProgress newParserProgress = new ParserProgress(id, title);
        ParserProgress existing = parsers.putIfAbsent(id, newParserProgress);
        if (existing != null) {
            throw new RuntimeException("Progress bar with this id is already exist");
        }
        return newParserProgress;
    }

    public int getParsedVacanciesCnt() {
        return parsedVacanciesCnt.get();
    }

    public int getNewVacanciesCnt() {
        return newVacanciesCnt.get();
    }

    public int getOutdatedVacanciesCnt() {
        return outdatedVacanciesCnt.get();
    }

    public void addParsedVacancies(int parsedVacancies) {
        parsedVacanciesCnt.addAndGet(parsedVacancies);
    }

    public void addNewVacancies(int newVacancies) {
        newVacanciesCnt.addAndGet(newVacancies);
    }

    public void addOutdatedVacancies(int outdatedVacancies) {
        outdatedVacanciesCnt.addAndGet(outdatedVacancies);
    }

    public ParsingProgressDTO getProgress() {
        List<ParserProgress> parsers = new ArrayList<>(this.parsers.values());
        parsers.sort(Comparator.comparingInt(ParserProgress::getId));

        return ParsingProgressDTO.builder()
                .parsingHash(parsingHash)
                .parsers(parsers)
                .status(status)
                .finished(status == ParsingStatusEnum.FINISHED || status == ParsingStatusEnum.STOPPED)
                .parsedVacanciesTotalCount(parsedVacanciesCnt.get())
                .newVacanciesTotalCount(newVacanciesCnt.get())
                .outdatedVacanciesTotalCount(outdatedVacanciesCnt.get())
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();
    }
}
