package com.github.yehortpk.crawler.exceptions;

import lombok.Getter;

/**
 * A {@code {name}} placeholder in a page's fetch settings has no value: neither {@code {page}} nor one of the
 * placeholders the crawler's metadata call returned. Fails every page of the company before any request is sent.
 */
@Getter
public class UnresolvedPlaceholderException extends RuntimeException {
    private final String placeholder;
    private final String location;

    public UnresolvedPlaceholderException(String placeholder, String location) {
        super(String.format("Placeholder %s in the %s has no value", placeholder, location));
        this.placeholder = placeholder;
        this.location = location;
    }
}
