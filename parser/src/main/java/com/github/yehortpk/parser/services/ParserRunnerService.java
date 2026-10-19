package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.exceptions.NoVacanciesOnPageException;
import com.github.yehortpk.parser.models.PageDTO;
import com.github.yehortpk.parser.parser.SiteParser;
import com.github.yehortpk.parser.exceptions.ParsingAlreadyStartedException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.scrapper.site.SiteScrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static com.github.yehortpk.parser.utils.LogUtils.createErrorMessage;

/**
 * This class implements the {@link ApplicationRunner} interface and is responsible for parsing vacancies,
 * identifying new and outdated ones, and notifying about new vacancies.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ParserRunnerService {
    private final ApplicationContext applicationContext;
    private final CompanyService companyService;
    private final NotifierService notifierService;
    private final ParsingProgressService parsingProgressService;
    private final ParsingHistoryService parsingHistoryService;
    private final VacancyService vacancyService;

    private Thread runnerThread;

    public void runParsing() throws ParsingAlreadyStartedException {
        if (runnerThread == null || !runnerThread.isAlive()) {
            parsingProgressService.init();
            runnerThread = new Thread(this::runParsers);
            runnerThread.start();
        } else {
            throw new ParsingAlreadyStartedException("Parsing process has already started");
        }
    }

    private void runParsers() {
        List<CompanyDTO> companies = companyService.getCompaniesList();

        if (companies.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            List<CompletableFuture<Void>> companyFutures = companies.stream()
                    .map(company -> parseCompanyAsync(company, executor))
                    .filter(Objects::nonNull)
                    .toList();

            CompletableFuture.allOf(companyFutures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        finishParsing();
    }

    private void finishParsing() {
        parsingProgressService.setFinished(true);
        parsingHistoryService.saveProgress(parsingProgressService.getProgress());

        log.info("""
                Parsing completed.\s
                Total vacancies parsed: %s\s
                New vacancies count: %s\s
                Outdated vacancies count: %s
                """.formatted(
                parsingProgressService.getParsedVacanciesCnt(),
                parsingProgressService.getNewVacanciesCnt(),
                parsingProgressService.getOutdatedVacanciesCnt()
        ));
    }

    /**
     * Kicks off scrapping and parsing for a single company: scrap its pages, parse vacancies from
     * each page in parallel, then reconcile parsed vacancies against what's already persisted.
     * Returns null (instead of a future) if the company has no registered scrapper bean.
     */
    private CompletableFuture<Void> parseCompanyAsync(CompanyDTO company, ExecutorService executor) {
        SiteScrapper siteScrapper = resolveScrapper(company);
        if (siteScrapper == null) {
            return null;
        }

        int companyId = company.getCompanyId();
        List<VacancyDTO> persistedVacancies = companyService.getPersistedVacancies(companyId);
        parsingProgressService.addParserProgress(companyId, company.getTitle());

        return CompletableFuture
                .supplyAsync(() -> siteScrapper.scrapCompanyVacancies(company), executor)
                .exceptionally(ex -> {
                    handleCompanyScrapperException(ex, company);
                    return List.of();
                })
                .thenCompose(pageFutures -> parseAllPages(pageFutures, company, persistedVacancies, executor))
                .thenAccept(parsedVacancies -> handleOutdatedVacancies(parsedVacancies, persistedVacancies));
    }

    private SiteScrapper resolveScrapper(CompanyDTO company) {
        try {
            return (SiteScrapper) applicationContext.getBean(company.getBeanClass());
        } catch (BeansException ignored) {
            log.error("{} scrapper implementation doesn't exist", company.getBeanClass());
            return null;
        }
    }

    /**
     * Parses every scrapped page of a company concurrently and joins the results once all pages
     * are done, without blocking a shared thread inside the async chain.
     */
    private CompletableFuture<List<VacancyDTO>> parseAllPages(
            List<CompletableFuture<PageDTO>> pageFutures,
            CompanyDTO company,
            List<VacancyDTO> persistedVacancies,
            ExecutorService executor
    ) {
        List<CompletableFuture<List<VacancyDTO>>> pageResultFutures = new ArrayList<>();
        for (int i = 0; i < pageFutures.size(); i++) {
            int pageID = i + 1;
            pageResultFutures.add(
                    pageFutures.get(i)
                            .exceptionally(ex -> {
                                handlePageScrapperException(ex, company, pageID);
                                return null;
                            })
                            .thenCompose(page -> page == null
                                    ? CompletableFuture.completedFuture(List.<VacancyDTO>of())
                                    : CompletableFuture.supplyAsync(() -> parsePageVacancies(page, company, persistedVacancies), executor)
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

    private void handleCompanyScrapperException(Throwable ex, CompanyDTO company) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, error: %s",
                company.getTitle(), createErrorMessage((Exception) ex));

        log.error(errorLog);
        parserProgress.markPageError(1);
        parserProgress.addPageLog(1, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handlePageScrapperException(Throwable ex, CompanyDTO company, int pageID) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, pageID:%s, error: %s",
                company.getTitle(), pageID, createErrorMessage((Exception) ex));
        log.error(errorLog);
        parserProgress.markPageError(pageID);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handlePageParserException(Throwable ex, CompanyDTO company, int pageID) {
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        String errorLog = String.format("Company: %s, page: %s error: %s",
                company.getTitle(), pageID, createErrorMessage((Exception) ex));
        log.error(errorLog);
        parserProgress.markPageError(pageID);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.ERROR, errorLog);
    }

    private void handleOutdatedVacancies(List<VacancyDTO> parsedVacancies, List<VacancyDTO> persistedVacancies) {
        if (persistedVacancies.isEmpty()) {
            return;
        }

        int companyID = persistedVacancies.getFirst().getCompanyID();

        Set<VacancyDTO> outdatedVacancies = calculateOutdatedVacancies(parsedVacancies, persistedVacancies);
        List<VacancyDTO> vacanciesOnDeletion =
                persistedVacancies.stream().filter(vacancy -> vacancy.getDeleteAt() != null).toList();

        for (VacancyDTO vacancyOnDeletion : vacanciesOnDeletion) {
            outdatedVacancies.remove(vacancyOnDeletion);
            // In case of an outdated vacancy actually was marked outdated by a mistake
            // (e.g. page wasn't parsed) we remove deletion marker
            if (parsedVacancies.contains(vacancyOnDeletion)) {
                vacancyOnDeletion.setDeleteAt(null);
                vacancyService.updateVacancy(vacancyOnDeletion);
                // In case of an outdated vacancy period is expired - remove it
            } else if (vacancyOnDeletion.getDeleteAt().isBefore(LocalDateTime.now())) {
                vacancyService.deleteVacancy(vacancyOnDeletion.getVacancyID());
            }
        }

        // In case of newly outdated vacancy set expiration timeout of one week, after which,
        // if vacancy still wasn't parsed - remove it
        for (VacancyDTO outdatedVacancy : outdatedVacancies) {
            outdatedVacancy.setDeleteAt(LocalDateTime.now().plusWeeks(1));
            vacancyService.updateVacancy(outdatedVacancy);
        }

        int outdatedVacanciesCnt = outdatedVacancies.size();
        parsingProgressService.getParsers().get(companyID).setOutdatedVacanciesCnt(outdatedVacanciesCnt);
        parsingProgressService.addOutdatedVacancies(outdatedVacanciesCnt);
    }

    private List<VacancyDTO> parsePageVacancies(PageDTO page, CompanyDTO company, List<VacancyDTO> persistedVacancies) {
        SiteParser siteParser = (SiteParser) applicationContext.getBean(company.getBeanClass());
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());

        int pageID = page.getPageID();

        List<VacancyDTO> parsedVacancies;
        try {
            parsedVacancies = siteParser.parseVacancies(page);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (parsedVacancies.isEmpty()) {
            throw new NoVacanciesOnPageException(page.getPageID());
        }

        for (VacancyDTO vacancy : parsedVacancies) {
            vacancy.setCompanyID(company.getCompanyId());
            vacancy.setCompanyTitle(company.getTitle());
            vacancy.setParsedAt(LocalDateTime.now());
        }

        ParserProgress.PageProgress pageProgress = parserProgress.getPages().get(pageID - 1);

        Set<VacancyDTO> newVacancies = calculateNewVacancies(parsedVacancies, persistedVacancies);

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

        notifierService.notifyNewVacancies(newVacancies);

        return parsedVacancies;
    }

    /**
     * Calculate difference between parsed and persistent vacancies
     * @param parsedVacancies - total vacancies set
     * @param persistedVacancies - persistent vacancies set
     * @return set of new vacancies
     */
    private Set<VacancyDTO> calculateNewVacancies(List<VacancyDTO> parsedVacancies, List<VacancyDTO> persistedVacancies) {
        Set<VacancyDTO> parsedVacanciesSet = new HashSet<>(parsedVacancies);
        Set<VacancyDTO> persistedVacanciesSet = new HashSet<>(persistedVacancies);

        parsedVacanciesSet.removeAll(persistedVacanciesSet);

        return parsedVacanciesSet;
    }

    private Set<VacancyDTO> calculateOutdatedVacancies(List<VacancyDTO> parsedVacancies, List<VacancyDTO> persistedVacancies) {
        Set<VacancyDTO> parsedVacanciesSet = new HashSet<>(parsedVacancies);
        Set<VacancyDTO> persistedVacanciesSet = new HashSet<>(persistedVacancies);

        persistedVacanciesSet.removeAll(parsedVacanciesSet);

        return persistedVacanciesSet;
    }

}
