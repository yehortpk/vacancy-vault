package com.github.yehortpk.crawler.exceptions;

/**
 * A vacancy's own page could not be fetched. The vacancy keeps what its vacancies page said.
 */
public class VacancyScrapeException extends RuntimeException {
    public VacancyScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
