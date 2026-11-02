package com.github.yehortpk.parser.scrapper.site;

import com.github.yehortpk.parser.exceptions.ParsingStoppedException;
import com.github.yehortpk.parser.models.CompanyDTO;
import com.github.yehortpk.parser.models.PageDTO;
import com.github.yehortpk.parser.models.PageRequestParams;
import com.github.yehortpk.parser.scrapper.page.PageScrapper;
import com.github.yehortpk.parser.services.ParsingProgressService;
import org.jsoup.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SiteScrapperImplTest {
    private static final int HOUR_MS = 60 * 60 * 1000;

    private final SiteScrapperImpl siteScrapper = new SiteScrapperImpl() {
        @Override
        protected String generateVacanciesPageURL(int pageID) {
            return "https://example.com/page/" + pageID;
        }

        @Override
        protected Connection.Method generateRequestMethod(int pageID) {
            return Connection.Method.GET;
        }

        @Override
        protected int generateDelayBetweenPagesMS() {
            return HOUR_MS;
        }

        @Override
        protected PageScrapper generatePageScrapper(int pageID) {
            return mock(PageScrapper.class);
        }
    };

    private final ParsingProgressService parsingProgressService = new ParsingProgressService();

    private PageRequestParams createPageRequestParams() {
        return PageRequestParams.builder()
                .data(new HashMap<>())
                .cookies(new HashMap<>())
                .headers(new HashMap<>())
                .build();
    }

    @BeforeEach
    void initProgress() {
        parsingProgressService.init();
        parsingProgressService.addParserProgress(1, "Company");
        siteScrapper.parsingProgressService = parsingProgressService;
    }

    @Test
    @SuppressWarnings("unchecked")
    void ScrapPages_StopRequestedDuringStaggerDelay_ShouldSkipWaitingPageAtOnce() throws Exception {
        siteScrapper.company = CompanyDTO.builder().companyId(1).title("Company").build();

        List<CompletableFuture<PageDTO>> pages = (List<CompletableFuture<PageDTO>>) ReflectionTestUtils.invokeMethod(
                siteScrapper, "scrapPages", 2, createPageRequestParams());
        CompletableFuture<PageDTO> waitingPage = pages.get(1);
        assertFalse(waitingPage.isDone());

        parsingProgressService.requestStop();

        ExecutionException ex = assertThrows(ExecutionException.class, () -> waitingPage.get(5, TimeUnit.SECONDS));
        assertInstanceOf(ParsingStoppedException.class, ex.getCause());
    }
}
