package com.github.yehortpk.parser.exceptions;

import lombok.experimental.StandardException;

@StandardException
public class ParserProgressNotFoundException extends RuntimeException {
    public ParserProgressNotFoundException() {
        super("Parser progress with this id not found");
    }
}
