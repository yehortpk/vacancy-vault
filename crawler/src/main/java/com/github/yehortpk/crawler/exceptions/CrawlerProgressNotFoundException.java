package com.github.yehortpk.crawler.exceptions;

import lombok.experimental.StandardException;

@StandardException
public class CrawlerProgressNotFoundException extends RuntimeException {
    public CrawlerProgressNotFoundException() {
        super("Crawler progress with this id not found");
    }
}
