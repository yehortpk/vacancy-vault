package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.crawler.CompanyCrawler;
import com.github.yehortpk.crawler.exceptions.MetadataScrapeException;
import com.github.yehortpk.crawler.exceptions.MetadataParseException;
import com.github.yehortpk.crawler.exceptions.UnresolvedPlaceholderException;
import com.github.yehortpk.crawler.models.CompanyDTO;
import com.github.yehortpk.crawler.models.CrawledVacancy;
import com.github.yehortpk.crawler.progress.PageProgressStatusEnum;
import com.github.yehortpk.crawler.progress.CrawlerProgress;
import com.github.yehortpk.crawler.progress.CrawlerProgressStatusEnum;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.progress.CrawlingStatusEnum;
import com.github.yehortpk.crawler.scraper.PageScraperResponse;
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

class CrawlerRunnerServiceTest {
    private static final int TIMEOUT_MS = 5000;

    private final Map<String, CompanyCrawler> companyCrawlers = new HashMap<>();
    private final CompanyService companyService = mock(CompanyService.class);
    private final CrawlingHistoryService crawlingHistoryService = mock(CrawlingHistoryService.class);
    private final VacancyService vacancyService = mock(VacancyService.class);
    private final CrawlingProgressService crawlingProgressService = new CrawlingProgressService();
    private final CrawlerRunnerService crawlerRunnerService = new CrawlerRunnerService(
            companyCrawlers,
            companyService,
            mock(NotifierService.class),
            crawlingProgressService,
            crawlingHistoryService,
            vacancyService
    );

    private CompanyDTO createCompany(int companyId, String beanClass) {
        return CompanyDTO.builder().companyId(companyId).beanClass(beanClass).title("Company " + companyId).build();
    }

    private CrawlingProgressDTO awaitSavedProgress() {
        ArgumentCaptor<CrawlingProgressDTO> progress = ArgumentCaptor.forClass(CrawlingProgressDTO.class);
        verify(crawlingHistoryService, timeout(TIMEOUT_MS)).saveProgress(progress.capture());
        return progress.getValue();
    }

    /**
     * Runs one company whose crawler fails with the exception before any page is scheduled
     */
    private CrawlerProgress runCompanyFailingWith(RuntimeException exception) {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        when(crawler.scrapeCompanyVacancies(any())).thenThrow(exception);
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));

        crawlerRunnerService.runCrawling();

        awaitSavedProgress();
        return crawlingProgressService.getCrawlers().get(1);
    }

    @Test
    void RunCrawling_CompaniesListFails_ShouldSaveFailedRun() {
        when(companyService.getCompaniesList()).thenThrow(new IllegalStateException("Database is down"));

        crawlerRunnerService.runCrawling();

        CrawlingProgressDTO progress = awaitSavedProgress();
        assertEquals(CrawlingStatusEnum.FAILED, progress.getStatus());
    }

    @Test
    void RunCrawling_NoCompanies_ShouldSaveFinishedRun() {
        when(companyService.getCompaniesList()).thenReturn(List.of());

        crawlerRunnerService.runCrawling();

        CrawlingProgressDTO progress = awaitSavedProgress();
        assertEquals(CrawlingStatusEnum.FINISHED, progress.getStatus());
    }

    @Test
    void RunCrawling_CrawlerMissing_ShouldSkipCompany() {
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "missing-crawler")));

        crawlerRunnerService.runCrawling();

        CrawlingProgressDTO progress = awaitSavedProgress();
        assertEquals(CrawlingStatusEnum.FINISHED, progress.getStatus());
        assertTrue(crawlingProgressService.getCrawlers().isEmpty());
    }

    @Test
    void RunCrawling_PageFails_ShouldFinishCompanyWithoutReconciliation() {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        CompletableFuture<PageScraperResponse> failedPage = CompletableFuture.failedFuture(new IllegalStateException("Page failed"));
        when(crawler.scrapeCompanyVacancies(any())).thenReturn(List.of(failedPage));
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));
        when(vacancyService.getPersistedVacancies(1)).thenReturn(List.of(mock(CrawledVacancy.class)));

        crawlerRunnerService.runCrawling();

        awaitSavedProgress();
        CrawlerProgress company = crawlingProgressService.getCrawlers().get(1);
        assertEquals(CrawlerProgressStatusEnum.FINISHED, company.getStatus());
        assertEquals(List.of(1), company.getUncrawledPageIds());
        verify(vacancyService, never()).markOutdated(any(), any());
        verify(vacancyService, never()).restoreOutdated(any());
        verify(vacancyService, never()).deleteVacancies(any());
    }

    @Test
    void RunCrawling_MetadataScrapeFails_ShouldMarkCompanyError() {
        CrawlerProgress company = runCompanyFailingWith(new MetadataScrapeException("Connection refused", null));

        assertEquals(CrawlerProgressStatusEnum.ERROR, company.getStatus());
        assertEquals(PageProgressStatusEnum.STEP_ERROR, company.getPages().getFirst().getStatus());
    }

    @Test
    void RunCrawling_MetadataParseFails_ShouldMarkCompanyError() {
        CrawlerProgress company = runCompanyFailingWith(new MetadataParseException("No pagination", null));

        assertEquals(CrawlerProgressStatusEnum.ERROR, company.getStatus());
        assertEquals(PageProgressStatusEnum.STEP_ERROR, company.getPages().getFirst().getStatus());
    }

    @Test
    void RunCrawling_OtherExceptionBeforePages_ShouldFailAllPagesAndFinishCompany() {
        CompanyCrawler crawler = mock(CompanyCrawler.class);
        when(crawler.scrapeCompanyVacancies(any())).thenAnswer(invocation -> {
            crawlingProgressService.getCrawlers().get(1).initPages(3);
            throw new UnresolvedPlaceholderException("{nonce}", "data _ajax_nonce");
        });
        companyCrawlers.put("crawler", crawler);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "crawler")));

        crawlerRunnerService.runCrawling();

        awaitSavedProgress();
        CrawlerProgress company = crawlingProgressService.getCrawlers().get(1);
        assertEquals(CrawlerProgressStatusEnum.FINISHED, company.getStatus());
        assertEquals(List.of(1, 2, 3), company.getUncrawledPageIds());
        assertTrue(company.getPages().stream().allMatch(page -> page.getStatus() == PageProgressStatusEnum.STEP_ERROR));
    }

    @Test
    void RunCrawling_PersistedVacanciesFailForLaterCompany_ShouldWaitForEarlierCompany() throws Exception {
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

        crawlerRunnerService.runCrawling();

        verify(crawlingHistoryService, after(300).never()).saveProgress(any());
        assertEquals(CrawlingStatusEnum.RUNNING, crawlingProgressService.getStatus());

        scrapingAllowed.countDown();

        CrawlingProgressDTO progress = awaitSavedProgress();
        assertEquals(CrawlingStatusEnum.FINISHED, progress.getStatus());
        CrawlerProgress failedCompany = crawlingProgressService.getCrawlers().get(2);
        assertEquals(CrawlerProgressStatusEnum.ERROR, failedCompany.getStatus());
    }
}
