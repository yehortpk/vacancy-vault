package com.github.yehortpk.parser.exceptions;

/**
 * A fetched page could not be parsed into vacancies. Marks the page ERROR.
 */
public class VacanciesParseException extends RuntimeException {
    public VacanciesParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
