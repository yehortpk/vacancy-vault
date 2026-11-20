package com.github.yehortpk.parser.exceptions;

import lombok.experimental.StandardException;

@StandardException
public class ProgressNotFoundException extends RuntimeException {
    public ProgressNotFoundException() {
        super("Progress with this hash not found");
    }
}
