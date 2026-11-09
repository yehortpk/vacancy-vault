package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.scrapper.browser.BrowserInstance;
import com.github.yehortpk.parser.scrapper.browser.BrowserPool;
import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BrowserService {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    private static final String STEALTH_INIT_SCRIPT =
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined }); " +
            "Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] }); " +
            "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] }); " +
            "window.chrome = { runtime: {} };";

    private final BrowserPool browserPool;

    private Browser.NewContextOptions createContextOptions(Map<String, String> headers, String proxy) {
        Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Europe/Kyiv");

        if (!headers.isEmpty()) {
            newContextOptions.setExtraHTTPHeaders(headers);
        }

        if (proxy != null) {
            newContextOptions.setProxy(proxy);
        }

        return newContextOptions;
    }

    public PageScrapperResponse scrapPage(String pageURL, String dynamicQuerySelector, int timeout, Map<String, String> headers, String proxy) throws IOException {
        Browser.NewContextOptions contextOptions = createContextOptions(headers, proxy);

        BrowserInstance browserInstance = browserPool.borrowInstance();
        try {
            return scrapPage(browserInstance, pageURL, dynamicQuerySelector, timeout, contextOptions);
        } finally {
            browserPool.returnInstance(browserInstance);
        }
    }

    /**
     * Scraps a page in a context of its own, so cookies, headers and the proxy stay isolated per
     * request while the browser itself is reused. The context is always closed, otherwise contexts
     * would pile up inside a browser that now outlives the page.
     * <p>
     * Redirects need no handling here: {@link Page#navigate} follows them and returns the response
     * of the final page.
     */
    private PageScrapperResponse scrapPage(
            BrowserInstance browserInstance,
            String pageURL,
            String dynamicQuerySelector,
            int timeout,
            Browser.NewContextOptions contextOptions
    ) throws IOException {
        try (BrowserContext context = browserInstance.createContext(contextOptions);
             Page page = context.newPage()
        ) {
            page.addInitScript(STEALTH_INIT_SCRIPT);

            page.route("**/*.{png,jpg,jpeg,gif,webp}", Route::abort); // Block images
            page.route("**/*.css", Route::abort); // Block stylesheets
            page.route("**/*.woff2", Route::abort); // Block web fonts
            page.route("**/ads*", Route::abort); // Block ads

            page.route("**/*", Route::resume);
            Response response = page.navigate(pageURL);

            // Abort unnecessary awaiting if page response isn't 200 OK
            if (response.status() != 200) {
                throw new IOException(String.format("page: %s, response status %d", pageURL, response.status()));
            }

            try {
                page.waitForSelector(dynamicQuerySelector,
                        new Page.WaitForSelectorOptions().setTimeout(timeout * 1000).setState(WaitForSelectorState.ATTACHED));
            } catch (TimeoutError e) {
                throw new IOException(String.format("Dynamic element search timeout exception, page: %s", pageURL));
            } catch (PlaywrightException e) {
                throw new IOException(String.format("page: %s, playwright exception: %s", pageURL, e.getMessage()));
            }

            String body = page.innerHTML("body");

            Map<String, String> cookies = context.cookies(response.url()).stream()
                    .collect(Collectors.toMap(cookie -> cookie.name, cookie -> cookie.value, (first, duplicate) -> first));
            Map<String, String> headers = response.headers();

            return new PageScrapperResponse(headers, cookies, body);
        }
    }
}
