package com.github.yehortpk.crawler.progress;

/**
 * Status of a single company's crawler. {@link #ERROR} is final like {@link #FINISHED}: the crawler
 * failed before reaching its pages, because the metadata precall failed or the persisted vacancies
 * could not be loaded. Failed pages don't affect it, they are reported on the pages themselves.
 */
public enum CrawlerProgressStatusEnum {
    RUNNING,
    FINISHED,
    ERROR
}
