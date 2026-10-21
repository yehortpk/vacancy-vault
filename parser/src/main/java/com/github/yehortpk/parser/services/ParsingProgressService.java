package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

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

    @Setter
    @Getter
    private volatile boolean finished;

    public ParsingProgressService() {
        init();
    }

    public void init() {
        progressLock.lock();
        try {
            this.parsingHash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            this.parsedVacanciesCnt.set(0);
            this.newVacanciesCnt.set(0);
            this.outdatedVacanciesCnt.set(0);
            this.finished = false;

            this.parsers = new ConcurrentHashMap<>();
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
                .finished(finished)
                .parsedVacanciesTotalCount(parsedVacanciesCnt.get())
                .newVacanciesTotalCount(newVacanciesCnt.get())
                .outdatedVacanciesTotalCount(outdatedVacanciesCnt.get())
                .build();
    }
}
