package com.github.yehortpk.crawler.progress;

/**
 * Status of the whole crawling run. {@link #STOPPING} lasts from a stop request until the pages
 * already being scraped are done. {@link #FAILED} means the run itself broke down, as opposed to
 * single companies failing, which {@link CrawlerProgressStatusEnum#ERROR} reports.
 */
public enum CrawlingStatusEnum {
    IDLE,
    RUNNING,
    STOPPING,
    FINISHED,
    STOPPED,
    FAILED
}
