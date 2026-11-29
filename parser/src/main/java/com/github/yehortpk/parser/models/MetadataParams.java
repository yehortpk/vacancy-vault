package com.github.yehortpk.parser.models;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * What a company's metadata call found out. Every map is empty unless set.
 */
@Getter
@ToString
@Builder
public class MetadataParams {
    @Builder.Default
    private final int pagesCount = 1;
    /**
     * Values by placeholder name, written without braces. {@code page} is always provided on top of these
     */
    @Builder.Default
    private final Map<String, String> placeholders = Map.of();
    /**
     * Added to every page's headers, overriding by key
     */
    @Builder.Default
    private final Map<String, String> requestHeaders = Map.of();
    /**
     * Added to every page's cookies, overriding by key
     */
    @Builder.Default
    private final Map<String, String> requestCookies = Map.of();
}
