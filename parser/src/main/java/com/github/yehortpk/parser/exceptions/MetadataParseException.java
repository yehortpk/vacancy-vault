package com.github.yehortpk.parser.exceptions;

/**
 * The response of a company's metadata call could not be parsed. Marks the company ERROR.
 */
public class MetadataParseException extends RuntimeException {
    public MetadataParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
