package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.progress.CrawlingStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlingProgressServiceTest {
    private final CrawlingProgressService crawlingProgressService = new CrawlingProgressService();

    @Test
    void RequestStop_CrawlingRunning_ShouldCompleteStopSignal() {
        crawlingProgressService.init();

        assertTrue(crawlingProgressService.requestStop());

        assertEquals(CrawlingStatusEnum.STOPPING, crawlingProgressService.getStatus());
        assertTrue(crawlingProgressService.getStopSignal().isDone());
    }

    @Test
    void RequestStop_CrawlingNotRunning_ShouldNotCompleteStopSignal() {
        assertFalse(crawlingProgressService.requestStop());

        assertEquals(CrawlingStatusEnum.IDLE, crawlingProgressService.getStatus());
        assertFalse(crawlingProgressService.getStopSignal().isDone());
    }

    @Test
    void Init_AfterStopRequest_ShouldRenewStopSignal() {
        crawlingProgressService.init();
        crawlingProgressService.requestStop();
        CompletableFuture<Void> previousStopSignal = crawlingProgressService.getStopSignal();

        crawlingProgressService.init();

        assertNotSame(previousStopSignal, crawlingProgressService.getStopSignal());
        assertFalse(crawlingProgressService.getStopSignal().isDone());
    }

    @Test
    void MarkCompleted_StopRequested_ShouldSetStopped() {
        crawlingProgressService.init();
        crawlingProgressService.requestStop();

        crawlingProgressService.markCompleted();

        assertEquals(CrawlingStatusEnum.STOPPED, crawlingProgressService.getStatus());
    }

    @Test
    void MarkCompleted_CrawlingRunning_ShouldSetFinished() {
        crawlingProgressService.init();

        crawlingProgressService.markCompleted();

        assertEquals(CrawlingStatusEnum.FINISHED, crawlingProgressService.getStatus());
    }

    @Test
    void MarkFailed_StopRequested_ShouldReportFailedRunAsFinished() {
        crawlingProgressService.init();
        crawlingProgressService.requestStop();

        crawlingProgressService.markFailed();

        CrawlingProgressDTO progress = crawlingProgressService.getProgress();
        assertEquals(CrawlingStatusEnum.FAILED, progress.getStatus());
        assertTrue(progress.isFinished());
        assertNotNull(progress.getFinishedAt());
    }
}
