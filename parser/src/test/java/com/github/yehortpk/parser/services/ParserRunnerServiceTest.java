package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.PageDTO;
import com.github.yehortpk.parser.models.VacancyDTO;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParserProgressStatusEnum;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.progress.ParsingStatusEnum;
import com.github.yehortpk.parser.scrapper.site.SiteScrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ParserRunnerServiceTest {
    private static final int TIMEOUT_MS = 5000;

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final ParsingHistoryService parsingHistoryService = mock(ParsingHistoryService.class);
    private final VacancyService vacancyService = mock(VacancyService.class);
    private final ParsingProgressService parsingProgressService = new ParsingProgressService();
    private final ParserRunnerService parserRunnerService = new ParserRunnerService(
            applicationContext,
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

    @Test
    void RunParsing_CompaniesListFails_ShouldSaveFailedRun() {
        when(companyService.getCompaniesList()).thenThrow(new IllegalStateException("Router is down"));

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
    void RunParsing_PageFails_ShouldFinishCompanyWithoutReconciliation() {
        SiteScrapper siteScrapper = mock(SiteScrapper.class);
        CompletableFuture<PageDTO> failedPage = CompletableFuture.failedFuture(new IllegalStateException("Page failed"));
        when(siteScrapper.scrapCompanyVacancies(any())).thenReturn(List.of(failedPage));
        when(applicationContext.getBean("siteScrapper")).thenReturn(siteScrapper);
        when(companyService.getCompaniesList()).thenReturn(List.of(createCompany(1, "siteScrapper")));
        when(companyService.getPersistedVacancies(1)).thenReturn(List.of(mock(VacancyDTO.class)));

        parserRunnerService.runParsing();

        awaitSavedProgress();
        ParserProgress company = parsingProgressService.getParsers().get(1);
        assertEquals(ParserProgressStatusEnum.FINISHED, company.getStatus());
        assertEquals(List.of(1), company.getUnparsedPageIds());
        verifyNoInteractions(vacancyService);
    }

    @Test
    void RunParsing_PersistedVacanciesFailForLaterCompany_ShouldWaitForEarlierCompany() throws Exception {
        CountDownLatch scrappingAllowed = new CountDownLatch(1);
        SiteScrapper slowScrapper = mock(SiteScrapper.class);
        when(slowScrapper.scrapCompanyVacancies(any())).thenAnswer(invocation -> {
            scrappingAllowed.await();
            return List.<CompletableFuture<?>>of();
        });
        when(applicationContext.getBean("slowScrapper")).thenReturn(slowScrapper);
        when(applicationContext.getBean("failingScrapper")).thenReturn(mock(SiteScrapper.class));
        when(companyService.getCompaniesList()).thenReturn(List.of(
                createCompany(1, "slowScrapper"),
                createCompany(2, "failingScrapper")
        ));
        when(companyService.getPersistedVacancies(2)).thenThrow(new IllegalStateException("Router is down"));

        parserRunnerService.runParsing();

        verify(parsingHistoryService, after(300).never()).saveProgress(any());
        assertEquals(ParsingStatusEnum.RUNNING, parsingProgressService.getStatus());

        scrappingAllowed.countDown();

        ParsingProgressDTO progress = awaitSavedProgress();
        assertEquals(ParsingStatusEnum.FINISHED, progress.getStatus());
        ParserProgress failedCompany = parsingProgressService.getParsers().get(2);
        assertEquals(ParserProgressStatusEnum.ERROR, failedCompany.getStatus());
    }
}
