package com.github.yehortpk.crawler.exceptions;

/**
 * The metadata call of a company failed. Marks the company ERROR.
 */
public class MetadataScrapeException extends RuntimeException {
    public MetadataScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
