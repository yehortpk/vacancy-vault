package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.parser.SiteParser;
import com.github.yehortpk.parser.scrapper.page.HttpClientPageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.github.yehortpk.parser.scrapper.page.JsoupPageScrapper;
import com.github.yehortpk.parser.scrapper.site.SiteScrapperImpl;
import com.github.yehortpk.parser.scrapper.site.metadata.SiteMetadataParser;
import lombok.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.jsoup.nodes.Document;

import java.util.Map;

/**
 * Site parser based on static page with prepended data
 */
@Component
@ToString(callSuper = true)
public abstract class StaticSiteCrawler extends SiteScrapperImpl implements SiteMetadataParser, SiteParser {
    private final int DELAY_SEC = 1;

    @Override
    protected String generateVacanciesPageURL(int pageID) {
        return company.getVacanciesURL().replace("{page}", String.valueOf(pageID));
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
     * Default page body parser, using Jsoup
     * @param pageBody page body
     * @return Jsoup {@link Document}
     */
    protected Document parsePageBodyJSoup(String pageBody) {
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
