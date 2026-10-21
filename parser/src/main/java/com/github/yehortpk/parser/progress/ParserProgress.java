package com.github.yehortpk.parser.progress;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ParserProgress {
    @Getter
    private final int id;
    @Getter
    private final String title;
    @Getter
    private volatile int totalPages;
    /**
     * Pages of one company are parsed concurrently, so both this list and every page's counters
     * are accessed from several threads at once.
     */
    @Getter
    private volatile List<PageProgress> pages = new CopyOnWriteArrayList<>();

    /**
     * Accumulated atomically rather than with a plain {@code +=}, which loses updates when the
     * pages of this company report their counts from different threads.
     */
    private final AtomicInteger parsedVacanciesCnt = new AtomicInteger();
    private final AtomicInteger newVacanciesCnt = new AtomicInteger();
    private final AtomicInteger outdatedVacanciesCnt = new AtomicInteger();

    public ParserProgress(int id, String title) {
        this.id = id;
        this.title = title;
        this.totalPages = 1;
        this.initPages(totalPages);
    }

    public void initPages(int totalPages) {
        List<PageProgress> newPages = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            newPages.add(new PageProgress(i + 1));
        }
        this.pages = new CopyOnWriteArrayList<>(newPages);
        this.totalPages = totalPages;
    }

    public void markPageDone(int page) {
        pages.get(page - 1).setStatus(PageProgressStatusEnum.STEP_DONE);
    }

    public void markPageError(int page) {
        pages.get(page - 1).setStatus(PageProgressStatusEnum.STEP_ERROR);
    }

    /**
     * Whether every page of this company was parsed successfully. Outdated vacancies may only be
     * reconciled when this holds: a company whose pages partly failed yields an incomplete parsed
     * set, in which every vacancy that lived on a failed page looks outdated.
     */
    @JsonIgnore
    public boolean hasAllPagesParsed() {
        return pages.stream().allMatch(page -> page.getStatus() == PageProgressStatusEnum.STEP_DONE);
    }

    @JsonIgnore
    public List<Integer> getUnparsedPageIds() {
        return pages.stream()
                .filter(page -> page.getStatus() != PageProgressStatusEnum.STEP_DONE)
                .map(PageProgress::getId)
                .toList();
    }

    public void addPageLog(int page, LogLevelEnum level, String message) {
        pages.get(page - 1).addLog(new PageProgress.PageLog(page, level, LocalDateTime.now(), message));
    }

    public void addParsedVacancies(int parsedVacancies) {
        parsedVacanciesCnt.addAndGet(parsedVacancies);
    }

    public void addNewVacancies(int newVacancies) {
        newVacanciesCnt.addAndGet(newVacancies);
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

    public void setParsedVacanciesCnt(int parsedVacanciesCnt) {
        this.parsedVacanciesCnt.set(parsedVacanciesCnt);
    }

    public void setNewVacanciesCnt(int newVacanciesCnt) {
        this.newVacanciesCnt.set(newVacanciesCnt);
    }

    public void setOutdatedVacanciesCnt(int outdatedVacanciesCnt) {
        this.outdatedVacanciesCnt.set(outdatedVacanciesCnt);
    }

    @Getter
    public static class PageProgress {
        private final int id;
        @Setter
        private volatile PageProgressStatusEnum status = PageProgressStatusEnum.STEP_PENDING;
        @Setter
        private volatile int parsedVacanciesCnt = 0;
        @Setter
        private volatile int newVacanciesCnt = 0;
        private final List<PageLog> logs = Collections.synchronizedList(new ArrayList<>());

        public PageProgress(int id) {
            this.id = id;
        }

        public void addLog(PageLog log) {
            this.logs.add(log);
        }

        public record PageLog(int pageID, LogLevelEnum level, LocalDateTime timestamp, String message){}
    }

    public enum LogLevelEnum {
        ERROR,
        INFO
    }
}
