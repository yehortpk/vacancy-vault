package com.github.yehortpk.crawler.exceptions;

/**
 * A vacancies page could not be fetched. Marks the page ERROR.
 */
public class VacancyPageFetchException extends RuntimeException {
    public VacancyPageFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
