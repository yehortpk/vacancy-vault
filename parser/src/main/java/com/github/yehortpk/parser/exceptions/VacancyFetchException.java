package com.github.yehortpk.parser.exceptions;

/**
 * A vacancy's own page could not be fetched. The vacancy keeps what its vacancies page said.
 */
public class VacancyFetchException extends RuntimeException {
    public VacancyFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
