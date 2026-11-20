package com.github.yehortpk.parser.exceptions;

import lombok.experimental.StandardException;

@StandardException
public class ParserPageProgressNotFoundException extends RuntimeException {
    public ParserPageProgressNotFoundException() {
        super("Parser page progress with this page not found");
    }
}
