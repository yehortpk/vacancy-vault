package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.progress.ParsingStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingProgressServiceTest {
    private final ParsingProgressService parsingProgressService = new ParsingProgressService();

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
