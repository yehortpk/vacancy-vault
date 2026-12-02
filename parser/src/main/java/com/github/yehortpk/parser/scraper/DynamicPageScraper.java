package com.github.yehortpk.parser.scraper;

import com.github.yehortpk.parser.models.FetchSettings;
import com.github.yehortpk.parser.utils.browser.BrowserPool;
import com.github.yehortpk.parser.services.BrowserService;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Scraper for pages a browser has to render, based on Playwright. Resource-intensive: each page holds a browser of
 * the {@link BrowserPool} until it is rendered. The page counts as ready once {@code readySelector} is attached.
 * <p>
 * A GET sends {@code data} as the query string, URL-encoded and appended to any query the URL already has. A POST
 * is made by intercepting the page's navigation request and resuming it with the method and body.
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicPageScraper implements PageScraper {
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded; charset=UTF-8";

    private final BrowserPool browserPool;
    private final String readySelector;

    @Override
    public PageScraperResponse scrapePage(FetchSettings settings) throws IOException {
        Map<String, String> data = Optional.ofNullable(settings.getData()).orElse(Map.of());
        Map<String, String> headers = Optional.ofNullable(settings.getHeaders()).orElse(Map.of());
        Map<String, String> cookies = Optional.ofNullable(settings.getCookies()).orElse(Map.of());
        boolean isPost = settings.getConnectionMethod() == Connection.Method.POST;

        String pageURL = isPost ? settings.getPageURL() : appendQueryParams(settings.getPageURL(), data);
        String postContentType = isPost ? findPostContentType(headers) : null;
        String postData = isPost ? createPostData(data, postContentType) : null;

        log.info("Connecting to the page {}, method: {}, data: {}, headers: {}, cookies: {}",
                settings.getPageURL(), settings.getConnectionMethod(), data, headers, cookies);

        PageScraperResponse response = new BrowserService(browserPool).scrapePage(settings.getPageId(), pageURL,
                readySelector, settings.getTimeoutSec(), settings.isFollowRedirects(), headers, cookies, postData,
                postContentType);

        log.info("Connection to the page {}, method: {}, data: {}, headers: {} was established",
                settings.getPageURL(), settings.getConnectionMethod(), data, headers);

        return new PageScraperResponse(settings.getPageId(), settings.getPageURL(),
                response.getHeaders(), response.getCookies(), response.getBody());
    }

    private String findPostContentType(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(header -> CONTENT_TYPE.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FORM_URL_ENCODED);
    }

    private String createPostData(Map<String, String> data, String contentType) {
        if (isJsonContentType(contentType)) {
            return new GsonBuilder().disableHtmlEscaping().create().toJson(data);
        }

        return encodeParams(data);
    }

    private boolean isJsonContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType).equalsTypeAndSubtype(MediaType.APPLICATION_JSON);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String encodeParams(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /**
     * Appends URL-encoded params to the URL, keeping its existing query string if there is one
     * @param pageUrl base url, with or without query params
     * @param params params to append
     * @return final url
     */
    private String appendQueryParams(String pageUrl, Map<String, String> params) {
        if (params.isEmpty()) {
            return pageUrl;
        }

        String separator = pageUrl.contains("?") ? "&" : "?";
        return pageUrl + separator + encodeParams(params);
    }
}
