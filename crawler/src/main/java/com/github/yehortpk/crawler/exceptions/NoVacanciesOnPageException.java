package com.github.yehortpk.crawler.exceptions;

import lombok.Getter;
import lombok.experimental.StandardException;

@Getter
public class NoVacanciesOnPageException extends RuntimeException {
    private final int pageId;

    public NoVacanciesOnPageException(int pageId) {
        super(String.format("No vacancies on page %s", pageId));
        this.pageId = pageId;
    }
}
