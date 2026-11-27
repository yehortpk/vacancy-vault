package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.parser.SiteParser;
import com.github.yehortpk.parser.scrapper.page.HttpClientPageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.github.yehortpk.parser.scrapper.page.JsoupPageScrapper;
import com.github.yehortpk.parser.scrapper.site.SiteScrapperImpl;
import com.github.yehortpk.parser.scrapper.site.metadata.SiteMetadataParser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Site parser based on XHR requests for the pages
 */
@Component
@ToString(callSuper = true)
public abstract class APISiteCrawler extends SiteScrapperImpl implements SiteMetadataParser, SiteParser {
    private final int DELAY_SEC = 1;

    @Override
    protected String generateVacanciesPageURL(int pageID) {
        return company.getApiVacanciesURL();
    }

    @Override
    protected Connection.Method generateRequestMethod(int pageID) {
        return Connection.Method.GET;
    }

    @Override
    protected int generateDelayBetweenPagesMS() {
        return DELAY_SEC * 1000;
    }

    @Override
    protected PageScrapper generatePageScrapper(int pageID) {
        return new HttpClientPageScrapper();
    }

    /**
     * Uses default solution with GSON to parse page body
     * @param pageBody page body
     * @return Gson parsed page body
     */
    protected final HashMap<String, Object> parsePageBodyJSON(String pageBody) {
        String finalBody = pageBody.strip().replace("\n", "");

        Type type = new TypeToken<HashMap<String, Object>>() {}.getType();
        if (finalBody.strip().startsWith("[")) {
            finalBody = "{body:" + pageBody + "}";
        }

        return new Gson().fromJson(finalBody, type);
    }

    /**
     * Default page body parser, using Jsoup
     * @param pageBody page body
     * @return Jsoup {@link Document}
     */
    protected final Document parsePageBodyJSoup(String pageBody) {
        return Jsoup.parse(pageBody);
    }

    @Override
    public int extractPagesCount(PageScrapperResponse metadataResponse) {
        return 1;
    }

    @Override
    public Map<String, String> extractHeaders(PageScrapperResponse metadataResponse) {
        return metadataResponse.getHeaders();
    }

    @Override
    public Map<String, String> extractData(PageScrapperResponse metadataResponse) {
        return Map.of();
    }

    @Override
    public Map<String, String> extractCookies(PageScrapperResponse metadataResponse) {
        return metadataResponse.getCookies();
    }
}
