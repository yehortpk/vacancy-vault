package com.github.yehortpk.parser.exceptions;

/**
 * A vacancies page could not be fetched. Marks the page ERROR.
 */
public class VacanciesFetchException extends RuntimeException {
    public VacanciesFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
