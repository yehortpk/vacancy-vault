package com.github.yehortpk.crawler.exceptions;

import lombok.experimental.StandardException;

@StandardException
public class CrawlerPageProgressNotFoundException extends RuntimeException {
    public CrawlerPageProgressNotFoundException() {
        super("Crawler page progress with this page not found");
    }
}
