package com.github.yehortpk.parser.progress;

/**
 * Status of a single company's parser. {@link #ERROR} is final like {@link #FINISHED}: the parser
 * is done, but at least one of its pages was not parsed.
 */
public enum ParserProgressStatusEnum {
    RUNNING,
    FINISHED,
    ERROR
}
