package com.github.yehortpk.parser.controllers;

import com.github.yehortpk.parser.exceptions.ParserPageProgressNotFoundException;
import com.github.yehortpk.parser.exceptions.ParserProgressNotFoundException;
import com.github.yehortpk.parser.exceptions.ProgressNotFoundException;
import com.github.yehortpk.parser.models.history.ParserHistory;
import com.github.yehortpk.parser.models.history.ParserPageHistory;
import com.github.yehortpk.parser.models.history.ParsingHistory;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.services.ParserRunnerService;
import com.github.yehortpk.parser.services.ParsingHistoryService;
import com.github.yehortpk.parser.services.ParsingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsible for the admin's parser room: starting and stopping a run, following
 * the current one, and reading the stored ones.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/parser")
public class ParserController {
    private final ParserRunnerService parserRunnerService;
    private final ParsingProgressService parsingProgressService;
    private final ParsingHistoryService parsingHistoryService;

    @PostMapping("/start")
    public APIResponse startParsing() {
        parserRunnerService.runParsing();
        return new APIResponse(200, "Parsing has been started");
    }

    @PostMapping("/stop")
    public APIResponse stopParsing() {
        parserRunnerService.stopParsing();
        return new APIResponse(200, "Parsing is stopping");
    }

    @GetMapping("/progress")
    public ParsingProgressDTO getProgress() {
        return parsingProgressService.getProgress();
    }

    @GetMapping("/progress/{parsing_hash}")
    public ParsingHistory getParsingHistory(@PathVariable("parsing_hash") String parsingHash) {
        return parsingHistoryService.findByParsingHash(parsingHash).orElseThrow(ProgressNotFoundException::new);
    }

    @GetMapping("/progress/{parsing_hash}/parser/{parser_id}")
    public ParserHistory getParserHistory(@PathVariable("parsing_hash") String parsingHash,
                                          @PathVariable("parser_id") int parserID) {
        return getParsingHistory(parsingHash).getParsers().stream()
                .filter(parser -> parser.getId() == parserID)
                .findFirst().orElseThrow(ParserProgressNotFoundException::new);
    }

    @GetMapping("/progress/{parsing_hash}/parser/{parser_id}/page/{page_id}")
    public ParserPageHistory getParserPageHistory(@PathVariable("parsing_hash") String parsingHash,
                                                  @PathVariable("parser_id") int parserID,
                                                  @PathVariable("page_id") int pageID) {
        return getParserHistory(parsingHash, parserID).getPages().stream()
                .filter(page -> page.getId() == pageID)
                .findFirst().orElseThrow(ParserPageProgressNotFoundException::new);
    }
}
