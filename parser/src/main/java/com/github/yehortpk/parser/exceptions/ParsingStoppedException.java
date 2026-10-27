package com.github.yehortpk.parser.exceptions;

/**
 * Aborts a company or page step that starts after parsing was requested to stop.
 */
public class ParsingStoppedException extends RuntimeException {
    public ParsingStoppedException() {
        super("Parsing was stopped");
    }
}
