package com.github.yehortpk.parser.progress;

/**
 * Status of a single company's parser. {@link #ERROR} is final like {@link #FINISHED}: the parser
 * failed before reaching its pages, because the metadata precall failed or the persisted vacancies
 * could not be loaded. Failed pages don't affect it, they are reported on the pages themselves.
 */
public enum ParserProgressStatusEnum {
    RUNNING,
    FINISHED,
    ERROR
}
