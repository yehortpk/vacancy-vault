package com.github.yehortpk.crawler.scraper;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * A fetched page. {@code pageId} and {@code pageURL} are copied from the {@link com.github.yehortpk.crawler.models.ScrapeSettings}
 * the page was requested with; headers and cookies are the response's.
 */
@Getter
@AllArgsConstructor
public class PageScraperResponse {
    private final int pageId;
    private final String pageURL;
    private final Map<String, String> headers;
    private final Map<String, String> cookies;
    private final String body;
}
