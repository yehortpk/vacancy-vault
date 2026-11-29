package com.github.yehortpk.parser.exceptions;

/**
 * The metadata call of a company failed. Marks the company ERROR.
 */
public class MetadataFetchException extends RuntimeException {
    public MetadataFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
