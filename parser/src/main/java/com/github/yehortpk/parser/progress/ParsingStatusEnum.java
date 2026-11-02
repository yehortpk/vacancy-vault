package com.github.yehortpk.parser.progress;

/**
 * Status of the whole parsing run. {@link #STOPPING} lasts from a stop request until the pages
 * already being scrapped are done. {@link #FAILED} means the run itself broke down, as opposed to
 * single companies failing, which {@link ParserProgressStatusEnum#ERROR} reports.
 */
public enum ParsingStatusEnum {
    IDLE,
    RUNNING,
    STOPPING,
    FINISHED,
    STOPPED,
    FAILED
}
