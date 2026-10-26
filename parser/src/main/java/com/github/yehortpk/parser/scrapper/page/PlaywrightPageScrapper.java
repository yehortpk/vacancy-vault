package com.github.yehortpk.parser.scrapper.page;

import com.github.yehortpk.parser.models.PageRequestParams;
import com.github.yehortpk.parser.scrapper.browser.BrowserPool;
import com.github.yehortpk.parser.services.BrowserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;

/**
 * Component-based site page scrapper. Uses dynamicElementQuerySelector from {@link PageRequestParams}
 * to delay page scrapping until the element on the page is loaded. Works on Selenium
 */
@Slf4j
@RequiredArgsConstructor
public class PlaywrightPageScrapper implements PageScrapper {
    private final String dynamicElementQuerySelector;
    private final BrowserPool browserPool;

    @Override
    public PageScrapperResponse scrapPage(PageRequestParams pageRequestParams) throws IOException {
        Map<String, String> headers = pageRequestParams.getHeaders();
        String proxy = pageRequestParams.getProxy() == null? null: convertProxyToString(pageRequestParams.getProxy());
        String pageURL = constructURLWithData(pageRequestParams.getPageURL(), pageRequestParams.getData());

        BrowserService browserService = new BrowserService(browserPool);
        log.info("Connecting to the page {}, method: {}, proxy: {},  data: {}, headers: {}",
                pageRequestParams.getPageURL(),
                pageRequestParams.getConnectionMethod(),
                pageRequestParams.getProxy(),
                pageRequestParams.getData(),
                pageRequestParams.getHeaders()
        );
        PageScrapperResponse pageScrapperResponse = browserService.scrapPage(pageURL, dynamicElementQuerySelector, pageRequestParams.getTimeoutSec(), headers, proxy);

        log.info("Connection to the page {}, method: {}, , proxy: {},  data: {}, headers: {} was established",
                pageRequestParams.getPageURL(),
                pageRequestParams.getConnectionMethod(),
                pageRequestParams.getProxy(),
                pageRequestParams.getData(),
                pageRequestParams.getHeaders()
        );
        return pageScrapperResponse;
    }

    /**
     * Construct GET url with the data map as query params
     * @param pageUrl base url without query params
     * @param data data for query params
     * @return final url
     */
    private String constructURLWithData(String pageUrl, Map<String, String> data) {
        if (!data.isEmpty()) {
            StringBuilder pageUrlBuilder = new StringBuilder(pageUrl + "?");
            for (Map.Entry<String, String> dataES : data.entrySet()) {
                if (dataES.getKey().endsWith("[]")) {
                    for (String valuePart : dataES.getValue().split(",")) {
                        pageUrlBuilder.append(dataES.getKey()).append("=").append(valuePart).append("&");
                    }
                } else {
                    pageUrlBuilder.append(dataES.getKey()).append("=").append(dataES.getValue()).append("&");
                }

            }
            pageUrl = pageUrlBuilder.toString();
        }

        return pageUrl;
    }

    private String convertProxyToString(Proxy proxy) {
        InetSocketAddress addr = (InetSocketAddress) proxy.address();
        String protocol = proxy.type() == Proxy.Type.SOCKS ? "socks5" : "http";
        return protocol + "://" + addr.getHostString() + ":" + addr.getPort();
    }
}
