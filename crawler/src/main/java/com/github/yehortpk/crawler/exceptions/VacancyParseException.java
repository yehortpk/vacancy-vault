package com.github.yehortpk.crawler.exceptions;

/**
 * A vacancy's own page could not be parsed. The vacancy keeps what its vacancies page said.
 */
public class VacancyParseException extends RuntimeException {
    public VacancyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
