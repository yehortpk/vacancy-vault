package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.progress.ParsingStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingProgressServiceTest {
    private final ParsingProgressService parsingProgressService = new ParsingProgressService();

    @Test
    void RequestStop_ParsingRunning_ShouldCompleteStopSignal() {
        parsingProgressService.init();

        assertTrue(parsingProgressService.requestStop());

        assertEquals(ParsingStatusEnum.STOPPING, parsingProgressService.getStatus());
        assertTrue(parsingProgressService.getStopSignal().isDone());
    }

    @Test
    void RequestStop_ParsingNotRunning_ShouldNotCompleteStopSignal() {
        assertFalse(parsingProgressService.requestStop());

        assertEquals(ParsingStatusEnum.IDLE, parsingProgressService.getStatus());
        assertFalse(parsingProgressService.getStopSignal().isDone());
    }

    @Test
    void Init_AfterStopRequest_ShouldRenewStopSignal() {
        parsingProgressService.init();
        parsingProgressService.requestStop();
        CompletableFuture<Void> previousStopSignal = parsingProgressService.getStopSignal();

        parsingProgressService.init();

        assertNotSame(previousStopSignal, parsingProgressService.getStopSignal());
        assertFalse(parsingProgressService.getStopSignal().isDone());
    }

    @Test
    void MarkCompleted_StopRequested_ShouldSetStopped() {
        parsingProgressService.init();
        parsingProgressService.requestStop();

        parsingProgressService.markCompleted();

        assertEquals(ParsingStatusEnum.STOPPED, parsingProgressService.getStatus());
    }

    @Test
    void MarkCompleted_ParsingRunning_ShouldSetFinished() {
        parsingProgressService.init();

        parsingProgressService.markCompleted();

        assertEquals(ParsingStatusEnum.FINISHED, parsingProgressService.getStatus());
    }

    @Test
    void MarkFailed_StopRequested_ShouldReportFailedRunAsFinished() {
        parsingProgressService.init();
        parsingProgressService.requestStop();

        parsingProgressService.markFailed();

        ParsingProgressDTO progress = parsingProgressService.getProgress();
        assertEquals(ParsingStatusEnum.FAILED, progress.getStatus());
        assertTrue(progress.isFinished());
        assertNotNull(progress.getFinishedAt());
    }
}
