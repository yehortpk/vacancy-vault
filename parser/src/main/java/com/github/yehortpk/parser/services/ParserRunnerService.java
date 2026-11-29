package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.crawler.CompanyCrawler;
import com.github.yehortpk.parser.exceptions.MetadataFetchException;
import com.github.yehortpk.parser.exceptions.MetadataParseException;
import com.github.yehortpk.parser.exceptions.NoVacanciesOnPageException;
import com.github.yehortpk.parser.exceptions.ParsingAlreadyStartedException;
import com.github.yehortpk.parser.exceptions.ParsingNotRunningException;
import com.github.yehortpk.parser.exceptions.ParsingStoppedException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.ParsedVacancy;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParserProgressStatusEnum;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.scraper.page.PageScraperResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.github.yehortpk.parser.utils.LogUtils.createErrorMessage;

/**
 * This class is responsible for parsing vacancies, identifying new and outdated ones against the
 * vacancies published by earlier runs, and publishing the new ones. Outdated vacancies are only
 * marked here; {@code OutdatedVacancySweeper} drops them once their grace period has expired.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ParserRunnerService {
    /**
     * Every company crawler by its bean name, which is a company's {@code bean_class}
     */
    private final Map<String, CompanyCrawler> companyCrawlers;
    private final CompanyService companyService;
    private final NotifierService notifierService;
    private final ParsingProgressService parsingProgressService;
    private final ParsingHistoryService parsingHistoryService;
    private final VacancyService vacancyService;

    private Thread runnerThread;

    public synchronized void runParsing() throws ParsingAlreadyStartedException {
        if (runnerThread == null || !runnerThread.isAlive()) {
            parsingProgressService.init();
            runnerThread = new Thread(this::runParsers);
            runnerThread.start();
        } else {
            throw new ParsingAlreadyStartedException("Parsing process has already started");
        }
    }

    /**
     * Stops the running parsing cooperatively: every company and page step that has not started
     * yet is skipped and marked as failed, while pages already being scraped are left to finish,
     * since interrupting a borrowed browser mid-navigation would break the browser pool.
     */
    public synchronized void stopParsing() throws ParsingNotRunningException {
        if (runnerThread == null || !runnerThread.isAlive() || !parsingProgressService.requestStop()) {
            throw new ParsingNotRunningException("Parsing process is not running or is already stopping");
        }
        log.info("Parsing stop requested, waiting for the pages already being scraped");
    }

    private void runParsers() {
        try {
            parseCompanies(companyService.getCompaniesList());
            parsingProgressService.markCompleted();
        } catch (RuntimeException ex) {
            log.error("Parsing failed: {}", createErrorMessage(ex));
            parsingProgressService.markFailed();
        }

        finishParsing();
    }

    private void parseCompanies(List<CompanyDTO> companies) {
        // Metadata precalls wait on the network or the browser pool, so they need no cap of their
        // own, and the CPU-bound page parsing is already spread over a carrier thread per core
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> companyFutures = companies.stream()
                    .map(company -> parseCompanyAsync(company, executor))
                    .filter(Objects::nonNull)
                    .toList();

            CompletableFuture.allOf(companyFutures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    private void finishParsing() {
        notifierService.resetPublishedLinks();
        parsingHistoryService.saveProgress(parsingProgressService.getProgress());

        log.info("""
                Parsing completed with status %s.\s
                Total vacancies parsed: %s\s
                New vacancies count: %s\s
                Outdated vacancies count: %s
                """.formatted(
                parsingProgressService.getStatus(),
                parsingProgressService.getParsedVacanciesCnt(),
                parsingProgressService.getNewVacanciesCnt(),
                parsingProgressService.getOutdatedVacanciesCnt()
        ));
    }

    /**
     * Kicks off scraping and parsing for a single company: scrape its pages, parse vacancies from
     * each page in parallel, then reconcile parsed vacancies against what's already persisted.
     * Returns null (instead of a future) if the company has no registered crawler bean, or its
     * persisted vacancies could not be loaded.
     */
    private CompletableFuture<Void> parseCompanyAsync(CompanyDTO company, ExecutorService executor) {
        CompanyCrawler crawler = companyCrawlers.get(company.getBeanClass());
        if (crawler == null) {
            log.error("{} crawler implementation doesn't exist", company.getBeanClass());
            return null;
        }

        int companyId = company.getCompanyId();
        ParserProgress parserProgress = parsingProgressService.addParserProgress(companyId, company.getTitle());

        List<ParsedVacancy> persistedVacancies;
        try {
            persistedVacancies = vacancyService.getPersistedVacancies(companyId);
        } catch (RuntimeException ex) {
            // Handled here, since the companies before this one are already being parsed
            handlePersistedVacanciesException(ex, company);
            parserProgress.setStatus(ParserProgressStatusEnum.ERROR);
            return null;
        }

        Set<String> persistedLinks = persistedVacancies.stream()
                .map(ParsedVacancy::getLink)
                .collect(Collectors.toSet());

        // Pages are parsed concurrently against one snapshot of the persisted vacancies, so the
        // same vacancy can be classified as new by several pages. Links claimed here keep every
        // such vacancy counted once per company.
        Set<String> claimedNewLinks = ConcurrentHashMap.newKeySet();

        return CompletableFuture
                .supplyAsync(() -> {
                    if (parsingProgressService.isStopRequested()) {
                        throw new ParsingStoppedException();
                    }
                    return crawler.scrapeCompanyVacancies(company);
                }, executor)
                .exceptionally(ex -> {
                    handleCompanyScraperException(ex, company);
                    return List.of();
                })
                .thenCompose(pageFutures ->
                        parseAllPages(pageFutures, crawler, company, persistedLinks, claimedNewLinks, executor))
                .thenAccept(parsedVacancies ->
                        reconcileOutdatedVacancies(parsedVacancies, persistedVacancies, company))
                .handle((ignored, ex) -> {
                    if (ex != null) {
                        handleReconciliationException(ex, company);
                    }
                    // Stays ERROR after a failed metadata call. Failed pages don't affect it
                    if (parserProgress.getStatus() == ParserProgressStatusEnum.RUNNING) {
                        parserProgress.setStatus(ParserProgressStatusEnum.FINISHED);
                    }
                    return null;
                });
    }

    /**
     * Parses every scraped page of a company concurrently and joins the results once all pages
     * are done, without blocking a shared thread inside the async chain.
     */
    private CompletableFuture<List<VacancyDTO>> parseAllPages(
            List<CompletableFuture<PageScraperResponse>> pageFutures,
            CompanyCrawler crawler,
            CompanyDTO company,
            Set<String> persistedLinks,
            Set<String> claimedNewLinks,
            ExecutorService executor
    ) {
        List<CompletableFuture<List<VacancyDTO>>> pageResultFutures = new ArrayList<>();
        for (int i = 0; i < pageFutures.size(); i++) {
            int pageID = i + 1;
            pageResultFutures.add(
                    pageFutures.get(i)
                            .exceptionally(ex -> {
                                handlePageScraperException(ex, company, pageID);
                                return null;
                            })
                            .thenCompose(page -> page == null
                                    ? CompletableFuture.completedFuture(List.<VacancyDTO>of())
                                    : CompletableFuture.supplyAsync(
                                            () -> parsePageVacancies(page, crawler, company, persistedLinks, claimedNewLinks),
                                            executor)
                                            .exceptionally(ex -> {
                                                handlePageParserException(ex, company, pageID);
                                                return List.of();
                                            })
                            )
            );
        }

        return CompletableFuture.allOf(pageResultFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> pageResultFutures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .toList());
    }

    private void handlePersistedVacanciesException(Throwable ex, CompanyDTO company) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, error: %s",
                company.getTitle(), createStepErrorMessage(ex));

        log.error(errorLog);
        parserProgress.markPageError(1);
        parserProgress.addPageLog(1, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    /**
     * A failed metadata call, or a metadata response that can't be parsed, marks the company ERROR. Any other
     * failure before the pages were scheduled, such as a placeholder without a value, fails every page and leaves
     * the company to finish.
     */
    private void handleCompanyScraperException(Throwable ex, CompanyDTO company) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        String errorLog = String.format("Company: %s, error: %s",
                company.getTitle(), createStepErrorMessage(ex));
        log.error(errorLog);

        if (cause instanceof MetadataFetchException || cause instanceof MetadataParseException) {
            parserProgress.setStatus(ParserProgressStatusEnum.ERROR);
            parserProgress.markPageError(1);
        } else {
            parserProgress.markAllPagesError();
        }
        parserProgress.addPageLog(1, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handlePageScraperException(Throwable ex, CompanyDTO company, int pageID) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, pageID:%s, error: %s",
                company.getTitle(), pageID, createStepErrorMessage(ex));
        log.error(errorLog);
        parserProgress.markPageError(pageID);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handlePageParserException(Throwable ex, CompanyDTO company, int pageID) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, page: %s error: %s",
                company.getTitle(), pageID, createStepErrorMessage(ex));
        log.error(errorLog);
        parserProgress.markPageError(pageID);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handleReconciliationException(Throwable ex, CompanyDTO company) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, outdated vacancies reconciliation failed: %s",
                company.getTitle(), createStepErrorMessage(ex));
        log.error(errorLog);
        parserProgress.addPageLog(1, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    /**
     * A step skipped because of a stop request is logged as such, without the stack trace a real
     * failure gets.
     */
    private String createStepErrorMessage(Throwable ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof ParsingStoppedException) {
            return cause.getMessage();
        }
        return createErrorMessage((Exception) ex);
    }

    /**
     * Marks the persisted vacancies of a company that are gone from the site, and lifts the
     * deletion markers of those listed again.
     * <p>
     * Reconciliation is skipped entirely unless every page of the company was parsed successfully.
     * A failed page contributes no vacancies, so its still-live vacancies would otherwise be
     * indistinguishable from removed ones and be marked for deletion.
     * <p>
     * The one-week grace period lives in the parser alone: nothing outside it hears about an
     * outdated vacancy until {@code OutdatedVacancySweeper} drops it after the period.
     */
    private void reconcileOutdatedVacancies(
            List<VacancyDTO> parsedVacancies, List<ParsedVacancy> persistedVacancies, CompanyDTO company) {
        if (persistedVacancies.isEmpty()) {
            return;
        }

        int companyID = company.getCompanyId();
        ParserProgress parserProgress = parsingProgressService.getParsers().get(companyID);

        if (!parserProgress.hasAllPagesParsed()) {
            String skipLog = String.format(
                    "Company: %s, outdated vacancies reconciliation skipped: pages %s were not parsed",
                    company.getTitle(), parserProgress.getUnparsedPageIds());
            log.warn(skipLog);
            parserProgress.addPageLog(1, ParserProgress.LogLevelEnum.ERROR, skipLog);
            return;
        }

        Set<String> parsedLinks = parsedVacancies.stream().map(VacancyDTO::getLink).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();

        List<String> outdatedLinks = new ArrayList<>();
        List<String> restoredLinks = new ArrayList<>();

        for (ParsedVacancy persistedVacancy : persistedVacancies) {
            String link = persistedVacancy.getLink();
            boolean isStillListed = parsedLinks.contains(link);

            if (persistedVacancy.getDeleteAt() == null) {
                if (!isStillListed) {
                    outdatedLinks.add(link);
                }
                // In case of an outdated vacancy actually was marked outdated by a mistake
                // (e.g. page wasn't parsed) we remove deletion marker
            } else if (isStillListed) {
                restoredLinks.add(link);
            }
        }

        vacancyService.restoreOutdated(restoredLinks);

        // In case of newly outdated vacancy set expiration timeout of one week, after which,
        // if vacancy still wasn't parsed - remove it
        vacancyService.markOutdated(outdatedLinks, now.plusWeeks(1));

        int outdatedVacanciesCnt = outdatedLinks.size();
        parserProgress.setOutdatedVacanciesCnt(outdatedVacanciesCnt);
        parsingProgressService.addOutdatedVacancies(outdatedVacanciesCnt);
    }

    private List<VacancyDTO> parsePageVacancies(
            PageScraperResponse page,
            CompanyCrawler crawler,
            CompanyDTO company,
            Set<String> persistedLinks,
            Set<String> claimedNewLinks
    ) {
        if (parsingProgressService.isStopRequested()) {
            throw new ParsingStoppedException();
        }

        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());

        int pageID = page.getPageId();

        List<VacancyDTO> parsedVacancies = crawler.parseVacancies(page, company);

        if (parsedVacancies.isEmpty()) {
            throw new NoVacanciesOnPageException(page.getPageId());
        }

        for (VacancyDTO vacancy : parsedVacancies) {
            vacancy.setCompanyID(company.getCompanyId());
            vacancy.setCompanyTitle(company.getTitle());
            vacancy.setParsedAt(LocalDateTime.now());
        }

        ParserProgress.PageProgress pageProgress = parserProgress.getPages().get(pageID - 1);

        Set<VacancyDTO> newVacancies = calculateNewVacancies(parsedVacancies, persistedLinks, claimedNewLinks);

        int parsedVacanciesCnt = parsedVacancies.size();
        int newVacanciesCnt = newVacancies.size();

        parserProgress.markPageDone(pageID);

        String successLog = String.format("Company: %s, page: %s was parsed, parsed vacancies count = %s, new vacancies count = %s",
                company.getTitle(), pageID, parsedVacanciesCnt, newVacanciesCnt);
        log.info(successLog);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.INFO, successLog);

        pageProgress.setParsedVacanciesCnt(parsedVacanciesCnt);
        pageProgress.setNewVacanciesCnt(newVacanciesCnt);

        parserProgress.addParsedVacancies(parsedVacanciesCnt);
        parserProgress.addNewVacancies(newVacanciesCnt);

        parsingProgressService.addParsedVacancies(parsedVacanciesCnt);
        parsingProgressService.addNewVacancies(newVacanciesCnt);

        notifierService.notifyNewVacancies(newVacancies, company);

        return parsedVacancies;
    }

    /**
     * Calculate difference between parsed and persistent vacancies, excluding the vacancies another
     * page of the same company already claimed as new, so a vacancy listed on several pages is
     * counted and published once.
     * @param parsedVacancies - vacancies parsed from this page
     * @param persistedLinks - links of the persistent vacancies
     * @param claimedNewLinks - links already classified as new within this company's run
     * @return set of new vacancies
     */
    private Set<VacancyDTO> calculateNewVacancies(
            List<VacancyDTO> parsedVacancies, Set<String> persistedLinks, Set<String> claimedNewLinks) {
        Set<VacancyDTO> newVacancies = new HashSet<>(parsedVacancies);

        newVacancies.removeIf(vacancy -> persistedLinks.contains(vacancy.getLink()));
        newVacancies.removeIf(vacancy -> !claimedNewLinks.add(vacancy.getLink()));

        return newVacancies;
    }

}
