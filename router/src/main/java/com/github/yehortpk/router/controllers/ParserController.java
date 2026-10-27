package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.exception.ParserPageProgressNotFoundException;
import com.github.yehortpk.router.exception.ParserProgressNotFoundException;
import com.github.yehortpk.router.exception.ProgressNotFoundException;
import com.github.yehortpk.router.models.parser.ParserPageProgress;
import com.github.yehortpk.router.models.parser.ParserProgress;
import com.github.yehortpk.router.models.parser.ParsingProgress;
import com.github.yehortpk.router.models.parser.ParsingProgressDTO;
import com.github.yehortpk.router.models.response.APIResponse;
import com.github.yehortpk.router.services.ParsingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/parser")
public class ParserController {
    private final RestTemplate restTemplate;
    private final ParsingHistoryService parsingHistoryService;

    @Value("${parser-service-url}")
    private String parserServiceURL;

    @PostMapping("/start")
    public APIResponse startParsing() {
        return restTemplate.postForEntity(parserServiceURL + "/parser/start", null, APIResponse.class).getBody();
    }

    @PostMapping("/stop")
    public APIResponse stopParsing() {
        return restTemplate.postForEntity(parserServiceURL + "/parser/stop", null, APIResponse.class).getBody();
    }

    @GetMapping("/progress")
    public ParsingProgressDTO getParsingProgress() {
        return restTemplate.getForEntity(parserServiceURL + "/parser/progress", ParsingProgressDTO.class).getBody();
    }

    @GetMapping("/progress/{parsing_hash}")
    public ParsingProgress getParsingProgress(@PathVariable("parsing_hash") String parsingHash) {
        return parsingHistoryService.findByParsingHash(parsingHash).orElseThrow(ProgressNotFoundException::new);
    }

    @GetMapping("/progress/{parsing_hash}/parser/{parser_id}")
    public ParserProgress getParsingProgress(@PathVariable("parsing_hash") String parsingHash,
                                             @PathVariable("parser_id") int parserID) {
        ParsingProgress parsingProgress = getParsingProgress(parsingHash);

        return parsingProgress.getParsers().stream().filter(p -> p.getId() == parserID)
                .findFirst().orElseThrow(ParserProgressNotFoundException::new);
    }

    @GetMapping("/progress/{parsing_hash}/parser/{parser_id}/page/{page_id}")
    public ParserPageProgress getParsingProgress(@PathVariable("parsing_hash") String parsingHash,
                                                 @PathVariable("parser_id") int parserID,
                                                 @PathVariable("page_id") int pageID) {

        ParserProgress parserProgress = getParsingProgress(parsingHash, parserID);

        return parserProgress.getPages().stream().filter(p -> p.getId() == pageID)
                .findFirst().orElseThrow(ParserPageProgressNotFoundException::new);
    }

    @PostMapping("/progress")
    public void saveParsingProgress(@RequestBody ParsingProgressDTO parsingProgress) {
        parsingHistoryService.saveParsingProgress(parsingProgress);
    }
}
