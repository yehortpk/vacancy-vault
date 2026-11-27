package com.github.yehortpk.parser.scrapper.page;

import com.github.yehortpk.parser.models.PageRequestParams;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Pages scrapper based on the Jsoup library. Note Jsoup truncates response bodies over 2MB by default.
 * {@link HttpClientPageScrapper} is the default for static pages
 */
@Component
@Slf4j
public class JsoupPageScrapper implements PageScrapper {
    @Override
    public PageScrapperResponse scrapPage(PageRequestParams pageRequestParams) throws IOException {
        log.info("Connecting to the page {}, method: {}, proxy: {},  data: {}, headers: {}, cookies: {}",
                pageRequestParams.getPageURL(),
                pageRequestParams.getConnectionMethod(),
                pageRequestParams.getProxy(),
                pageRequestParams.getData(),
                pageRequestParams.getHeaders(),
                pageRequestParams.getCookies()
        );
        Connection.Response response = toConnection(pageRequestParams).execute();
        log.info("Connection to the page {}, method: {}, proxy: {},  data: {} was established",
                pageRequestParams.getPageURL(),
                pageRequestParams.getConnectionMethod(),
                pageRequestParams.getProxy(),
                pageRequestParams.getData()
        );
        return new PageScrapperResponse(response.headers(), response.cookies(), response.body());
    }

    /**
     * Transform {@link PageRequestParams} object into Jsoup {@link Connection}
     * @param pageRequestParams connection parameters of the page
     * @return page connection object
     */
    private Connection toConnection(PageRequestParams pageRequestParams) {
        Connection connection = Jsoup.connect(pageRequestParams.getPageURL())
                .proxy(pageRequestParams.getProxy())
                .headers(pageRequestParams.getHeaders())
                .cookies(pageRequestParams.getCookies())
                .ignoreContentType(true)
                .followRedirects(pageRequestParams.isFollowRedirects())
                .method(pageRequestParams.getConnectionMethod())
                .timeout(pageRequestParams.getTimeoutSec() * 1000)
                .data(pageRequestParams.getData());

        if (pageRequestParams.getConnectionMethod() == Connection.Method.POST &&
                pageRequestParams.getRequestBody() != null) {
            connection.requestBody(pageRequestParams.getRequestBody());
        }

        String userAgent = pageRequestParams.getHeaders().get("User-Agent");
        if (userAgent != null) {
            connection.userAgent(userAgent);
        }
        return connection;
    }


}
