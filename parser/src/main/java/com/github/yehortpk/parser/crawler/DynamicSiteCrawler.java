package com.github.yehortpk.parser.crawler;

import com.github.yehortpk.parser.parser.SiteParser;
import com.github.yehortpk.parser.scrapper.browser.BrowserPool;
import com.github.yehortpk.parser.scrapper.page.PlaywrightPageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.github.yehortpk.parser.scrapper.site.SiteScrapperImpl;
import com.github.yehortpk.parser.scrapper.site.metadata.SiteMetadataParser;
import lombok.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Site parser based on component frameworks. Dynamic element selector could be chosen to reduce page loading time.
 * It will be a signal to page ready for parsing. Most widely used #root or #app sections
 */
@Component
@ToString(callSuper = true)
public abstract class DynamicSiteCrawler extends SiteScrapperImpl implements SiteMetadataParser, SiteParser {
    private final int DELAY_SEC = 1;

    @Autowired
    protected BrowserPool browserPool;

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
        return new PlaywrightPageScrapper(createDynamicElementQuerySelector(), browserPool);
    }

    /**
     * Default page body parser, using Jsoup
     * @param pageBody page body
     * @return Jsoup {@link Document}
     */
    protected Document parsePageBodyJSoup(String pageBody) {
        return Jsoup.parse(pageBody);
    }

    /**
     * Dynamic element query selector which will be available after rendering. Parser is monitoring this element. If it
     * presents on the page it will be a signal to page ready for parsing
     * @return element query selector
     */
    protected String createDynamicElementQuerySelector() {
        return "body";
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
