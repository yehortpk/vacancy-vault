package com.github.yehortpk.parser.controllers;

import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.services.ParserRunnerService;
import com.github.yehortpk.parser.services.ParsingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/parser")
public class ParserController {
    private final ParserRunnerService parserRunnerService;
    private final ParsingProgressService parsingProgressService;

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
}
