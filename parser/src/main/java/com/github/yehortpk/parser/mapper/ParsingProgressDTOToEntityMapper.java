package com.github.yehortpk.parser.mapper;

import com.github.yehortpk.parser.models.history.ParserHistory;
import com.github.yehortpk.parser.models.history.ParserPageHistory;
import com.github.yehortpk.parser.models.history.ParserPageLog;
import com.github.yehortpk.parser.models.history.ParsingHistory;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the live progress of a run into the document stored for it. The progress objects are
 * still being written by the run's threads, so everything is copied rather than referenced.
 */
@Component
public class ParsingProgressDTOToEntityMapper {

    private ParserPageLog mapPageLog(int logId, ParserProgress.PageProgress.PageLog log) {
        return new ParserPageLog(logId, log.level(), log.timestamp(), log.message());
    }

    private ParserPageHistory mapPage(ParserProgress.PageProgress page) {
        // The list is synchronized, and copying it is the one compound action that needs the lock
        List<ParserProgress.PageProgress.PageLog> logs;
        synchronized (page.getLogs()) {
            logs = new ArrayList<>(page.getLogs());
        }

        List<ParserPageLog> pageLogs = new ArrayList<>();
        for (int i = 0; i < logs.size(); i++) {
            pageLogs.add(mapPageLog(i + 1, logs.get(i)));
        }
        return new ParserPageHistory(page.getId(), page.getStatus(), page.getParsedVacanciesCnt(), pageLogs);
    }

    private ParserHistory mapParser(ParserProgress parser) {
        return new ParserHistory(
                parser.getId(),
                parser.getTitle(),
                parser.getTotalPages(),
                parser.getStatus(),
                parser.getParsedVacanciesCnt(),
                parser.getNewVacanciesCnt(),
                parser.getOutdatedVacanciesCnt(),
                parser.getPages().stream().map(this::mapPage).toList());
    }

    public ParsingHistory mapToEntity(ParsingProgressDTO progress) {
        ParsingHistory history = new ParsingHistory();
        history.setParsingHash(progress.getParsingHash());
        history.setParsedVacancies(progress.getParsedVacanciesTotalCount());
        history.setNewVacancies(progress.getNewVacanciesTotalCount());
        history.setOutdatedVacancies(progress.getOutdatedVacanciesTotalCount());
        history.setStatus(progress.getStatus());
        history.setFinished(progress.isFinished());
        history.setParsers(progress.getParsers().stream().map(this::mapParser).toList());
        history.setStartedAt(progress.getStartedAt());
        history.setFinishedAt(progress.getFinishedAt());
        return history;
    }
}
