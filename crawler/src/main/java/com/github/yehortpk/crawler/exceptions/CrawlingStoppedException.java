package com.github.yehortpk.crawler.exceptions;

/**
 * Aborts a company or page step that starts after crawling was requested to stop.
 */
public class CrawlingStoppedException extends RuntimeException {
    public CrawlingStoppedException() {
        super("Crawling was stopped");
    }
}
