package com.github.yehortpk.crawler.exceptions;

/**
 * A vacancies page could not be fetched. Marks the page ERROR.
 */
public class VacancyPageScrapeException extends RuntimeException {
    public VacancyPageScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
