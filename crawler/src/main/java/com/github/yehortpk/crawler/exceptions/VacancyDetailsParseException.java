package com.github.yehortpk.crawler.exceptions;

/**
 * A vacancy's own page could not be parsed. The vacancy keeps what its vacancies page said.
 */
public class VacancyDetailsParseException extends RuntimeException {
    public VacancyDetailsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
