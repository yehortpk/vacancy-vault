package com.github.yehortpk.parser.progress;

/**
 * Status of the whole parsing run. {@link #STOPPING} lasts from a stop request until the pages
 * already being scrapped are done.
 */
public enum ParsingStatusEnum {
    IDLE,
    RUNNING,
    STOPPING,
    FINISHED,
    STOPPED
}
