package com.github.yehortpk.parser.models;

import com.github.yehortpk.parser.exceptions.UnresolvedPlaceholderException;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jsoup.Connection;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How one page is requested. A crawler describes it per page in
 * {@code DefaultCompanyCrawler.configureFetchSettings}, leaving {@code null} the data, headers or cookies that
 * should come from the company config. Immutable: every change goes through {@link #toBuilder()} or {@link #fillPlaceholders(Map)}, which return a
 * new instance.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class FetchSettings {
    /**
     * A {@code {name}} placeholder. Names hold no whitespace, quotes or braces, so JSON-like values are not taken
     * for placeholders.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([\\w.\\-]+)}");

    /**
     * Set by {@code DefaultCompanyCrawler}; crawlers don't set it
     */
    private final int pageId;
    /**
     * Always set by the crawler; never filled from the company
     */
    private final String pageURL;
    /**
     * The only request payload: the query string of a GET, the body of a POST. {@code null} until filled from the
     * company's data
     */
    private final Map<String, String> data;
    /**
     * {@code null} until filled from the company's headers
     */
    private final Map<String, String> headers;
    /**
     * {@code null} until filled from the company's cookies
     */
    private final Map<String, String> cookies;
    @Builder.Default
    private final Connection.Method connectionMethod = Connection.Method.GET;
    @Builder.Default
    private final int timeoutSec = 60;
    @Builder.Default
    private final boolean followRedirects = true;
    /**
     * How long this page waits before it starts
     */
    @Builder.Default
    private final Duration delay = Duration.ZERO;

    /**
     * Whether the value holds a {@code {name}} placeholder
     */
    public static boolean hasPlaceholder(String value) {
        return value != null && PLACEHOLDER.matcher(value).find();
    }

    /**
     * Replaces every {@code {name}} in the page URL and in the values of data and headers.
     *
     * @param placeholders values by placeholder name, written without braces
     * @return a new instance with every placeholder filled
     * @throws UnresolvedPlaceholderException if a placeholder has no value
     */
    public FetchSettings fillPlaceholders(Map<String, String> placeholders) {
        return toBuilder()
                .pageURL(fillValue(pageURL, placeholders, "page URL"))
                .data(fillValues(data, placeholders, "data"))
                .headers(fillValues(headers, placeholders, "header"))
                .build();
    }

    private static Map<String, String> fillValues(Map<String, String> values, Map<String, String> placeholders,
                                                  String location) {
        if (values == null) {
            return null;
        }

        Map<String, String> filledValues = new LinkedHashMap<>();
        values.forEach((key, value) ->
                filledValues.put(key, fillValue(value, placeholders, location + " " + key)));
        return Collections.unmodifiableMap(filledValues);
    }

    private static String fillValue(String value, Map<String, String> placeholders, String location) {
        if (value == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder filledValue = new StringBuilder();
        while (matcher.find()) {
            String placeholderValue = placeholders.get(matcher.group(1));
            if (placeholderValue == null) {
                throw new UnresolvedPlaceholderException(matcher.group(), location);
            }
            matcher.appendReplacement(filledValue, Matcher.quoteReplacement(placeholderValue));
        }
        matcher.appendTail(filledValue);
        return filledValue.toString();
    }
}
