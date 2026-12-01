package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.crawler.CompanyCrawler;
import com.github.yehortpk.parser.exceptions.MetadataFetchException;
import com.github.yehortpk.parser.exceptions.MetadataParseException;
import com.github.yehortpk.parser.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.ParsedVacancy;
import com.github.yehortpk.parser.progress.PageProgressStatusEnum;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParserProgressStatusEnum;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.progress.ParsingStatusEnum;
import com.github.yehortpk.parser.scraper.PageScraperResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParserRunnerServiceTest {
    private static final int TIMEOUT_MS = 5000;

    private final Map<String, CompanyCrawler> companyCrawlers = new HashMap<>();
    private final CompanyService companyService = mock(CompanyService.class);
    private final ParsingHistoryService parsingHistoryService = mock(ParsingHistoryService.class);
    private final VacancyService vacancyService = mock(VacancyService.class);
    private final ParsingProgressService parsingProgressService = new ParsingProgressService();
    private final ParserRunnerService parserRunnerService = new ParserRunnerService(
            companyCrawlers,
            companyService,
            mock(NotifierService.class),
            parsingProgressService,
            parsingHistoryService,
            vacancyService
    );

    private CompanyDTO createCompany(int companyId, String beanClass) {
        return CompanyDTO.builder().companyId(companyId).beanClass(beanClass).title("Company " + companyId).build();
    }

    private ParsingProgressDTO awaitSavedProgress() {
        ArgumentCaptor<ParsingProgressDTO> progress = ArgumentCaptor.forClass(ParsingProgressDTO.class);
        verify(parsingHistoryService, timeout(TIMEOUT_MS)).saveProgress(progress.capture());
        return progress.getValue();
    }

    /**
     * Runs one company whose crawler fails with the exception before any page is scheduled
     */
    private ParserProgress runCompanyFailingWith(RuntimeException exception) {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        when(crawler.scrapeCompanyVacancies(any())).thenThrow(exception);
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));

        parserRunnerService.runParsing();

        awaitSavedProgress();
        return parsingProgressService.getParsers().get(1);
    }

    @Test
    void RunParsing_CompaniesListFails_ShouldSaveFailedRun() {
        when(companyService.getCompaniesList()).thenThrow(new IllegalStateException("Database is down"));

        parserRunnerService.runParsing();

        ParsingProgressDTO progress = awaitSavedProgress();
        assertEquals(ParsingStatusEnum.FAILED, progress.getStatus());
    }

    @Test
    void RunParsing_NoCompanies_ShouldSaveFinishedRun() {
        when(companyService.getCompaniesList()).thenReturn(List.of());

        parserRunnerService.runParsing();

        ParsingProgressDTO progress = awaitSavedProgress();
        assertEquals(ParsingStatusEnum.FINISHED, progress.getStatus());
    }

    @Test
    void RunParsing_CrawlerMissing_ShouldSkipCompany() {
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "missing-crawler")));

        parserRunnerService.runParsing();

        ParsingProgressDTO progress = awaitSavedProgress();
        assertEquals(ParsingStatusEnum.FINISHED, progress.getStatus());
        assertTrue(parsingProgressService.getParsers().isEmpty());
    }

    @Test
    void RunParsing_PageFails_ShouldFinishCompanyWithoutReconciliation() {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        CompletableFuture<PageScraperResponse> failedPage = CompletableFuture.failedFuture(new IllegalStateException("Page failed"));
        when(crawler.scrapeCompanyVacancies(any())).thenReturn(List.of(failedPage));
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));
        when(vacancyService.getPersistedVacancies(1)).thenReturn(List.of(mock(ParsedVacancy.class)));

        parserRunnerService.runParsing();

        awaitSavedProgress();
        ParserProgress company = parsingProgressService.getParsers().get(1);
        assertEquals(ParserProgressStatusEnum.FINISHED, company.getStatus());
        assertEquals(List.of(1), company.getUnparsedPageIds());
        verify(vacancyService, never()).markOutdated(any(), any());
        verify(vacancyService, never()).restoreOutdated(any());
        verify(vacancyService, never()).deleteVacancies(any());
    }

    @Test
    void RunParsing_MetadataFetchFails_ShouldMarkCompanyError() {
        ParserProgress company = runCompanyFailingWith(new MetadataFetchException("Connection refused", null));

        assertEquals(ParserProgressStatusEnum.ERROR, company.getStatus());
        assertEquals(PageProgressStatusEnum.STEP_ERROR, company.getPages().getFirst().getStatus());
    }

    @Test
    void RunParsing_MetadataParseFails_ShouldMarkCompanyError() {
        ParserProgress company = runCompanyFailingWith(new MetadataParseException("No pagination", null));

        assertEquals(ParserProgressStatusEnum.ERROR, company.getStatus());
        assertEquals(PageProgressStatusEnum.STEP_ERROR, company.getPages().getFirst().getStatus());
    }

    @Test
    void RunParsing_OtherExceptionBeforePages_ShouldFailAllPagesAndFinishCompany() {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        when(crawler.scrapeCompanyVacancies(any())).thenAnswer(invocation -> {
            parsingProgressService.getParsers().get(1).initPages(3);
            throw new UnresolvedPlaceholderException("{nonce}", "data _ajax_nonce");
        });
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));

        parserRunnerService.runParsing();

        awaitSavedProgress();
        ParserProgress company = parsingProgressService.getParsers().get(1);
        assertEquals(ParserProgressStatusEnum.FINISHED, company.getStatus());
        assertEquals(List.of(1, 2, 3), company.getUnparsedPageIds());
        assertTrue(company.getPages().stream().allMatch(page -> page.getStatus() == PageProgressStatusEnum.STEP_ERROR));
    }

    @Test
    void RunParsing_PersistedVacanciesFailForLaterCompany_ShouldWaitForEarlierCompany() throws Exception {
        CountDownLatch scrapingAllowed = new CountDownLatch(1);
        CompanyCrawler slowCrawler = mock(CompanyCrawler.class);
        when(slowCrawler.scrapeCompanyVacancies(any())).thenAnswer(invocation -> {
            scrapingAllowed.await();
            return List.<CompletableFuture<PageScraperResponse>>of();
        });
        companyCrawlers.put("slowCrawler", slowCrawler);
        companyCrawlers.put("failingCrawler", mock(CompanyCrawler.class));
        when(companyService.getCompaniesList()).thenReturn(List.of(
                createCompany(1, "slowCrawler"),
                createCompany(2, "failingCrawler")
        ));
        when(vacancyService.getPersistedVacancies(2)).thenThrow(new IllegalStateException("Database is down"));

        parserRunnerService.runParsing();

        verify(parsingHistoryService, after(300).never()).saveProgress(any());
        assertEquals(ParsingStatusEnum.RUNNING, parsingProgressService.getStatus());

        scrapingAllowed.countDown();

        ParsingProgressDTO progress = awaitSavedProgress();
        assertEquals(ParsingStatusEnum.FINISHED, progress.getStatus());
        ParserProgress failedCompany = parsingProgressService.getParsers().get(2);
        assertEquals(ParserProgressStatusEnum.ERROR, failedCompany.getStatus());
    }
}
