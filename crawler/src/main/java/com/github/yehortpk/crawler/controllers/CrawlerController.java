package com.github.yehortpk.crawler.controllers;

import com.github.yehortpk.crawler.exceptions.CrawlerPageProgressNotFoundException;
import com.github.yehortpk.crawler.exceptions.CrawlerProgressNotFoundException;
import com.github.yehortpk.crawler.exceptions.ProgressNotFoundException;
import com.github.yehortpk.crawler.models.history.CrawlerHistory;
import com.github.yehortpk.crawler.models.history.CrawlerPageHistory;
import com.github.yehortpk.crawler.models.history.CrawlingHistory;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.services.CrawlerRunnerService;
import com.github.yehortpk.crawler.services.CrawlingHistoryService;
import com.github.yehortpk.crawler.services.CrawlingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller responsible for the admin's crawler room: starting and stopping a run, following
 * the current one, and reading the stored ones.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/crawler")
public class CrawlerController {
    private final CrawlerRunnerService crawlerRunnerService;
    private final CrawlingProgressService crawlingProgressService;
    private final CrawlingHistoryService crawlingHistoryService;

    @PostMapping("/start")
    public APIResponse startCrawling() {
        crawlerRunnerService.runCrawling();
        return new APIResponse(200, "Crawling has been started");
    }

    @PostMapping("/stop")
    public APIResponse stopCrawling() {
        crawlerRunnerService.stopCrawling();
        return new APIResponse(200, "Crawling is stopping");
    }

    @GetMapping("/progress")
    public CrawlingProgressDTO getProgress() {
        return crawlingProgressService.getProgress();
    }

    /** The stored runs without their companies, newest first. */
    @GetMapping("/history")
    public List<CrawlingHistory> getCrawlingHistories() {
        return crawlingHistoryService.findSummaries();
    }

    @GetMapping("/progress/{crawling_hash}")
    public CrawlingHistory getCrawlingHistory(@PathVariable("crawling_hash") String crawlingHash) {
        return crawlingHistoryService.findByCrawlingHash(crawlingHash).orElseThrow(ProgressNotFoundException::new);
    }

    @GetMapping("/progress/{crawling_hash}/crawler/{crawler_id}")
    public CrawlerHistory getCrawlerHistory(@PathVariable("crawling_hash") String crawlingHash,
                                          @PathVariable("crawler_id") int crawlerID) {
        return getCrawlingHistory(crawlingHash).getCrawlers().stream()
                .filter(crawler -> crawler.getId() == crawlerID)
                .findFirst().orElseThrow(CrawlerProgressNotFoundException::new);
    }

    @GetMapping("/progress/{crawling_hash}/crawler/{crawler_id}/page/{page_id}")
    public CrawlerPageHistory getCrawlerPageHistory(@PathVariable("crawling_hash") String crawlingHash,
                                                  @PathVariable("crawler_id") int crawlerID,
                                                  @PathVariable("page_id") int pageID) {
        return getCrawlerHistory(crawlingHash, crawlerID).getPages().stream()
                .filter(page -> page.getId() == pageID)
                .findFirst().orElseThrow(CrawlerPageProgressNotFoundException::new);
    }
}
