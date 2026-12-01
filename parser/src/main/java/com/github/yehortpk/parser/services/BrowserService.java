package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.utils.browser.BrowserInstance;
import com.github.yehortpk.parser.utils.browser.BrowserPool;
import com.github.yehortpk.parser.scraper.page.PageScraperResponse;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private Browser.NewContextOptions createContextOptions(Map<String, String> headers) {
        Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Europe/Kyiv");

        if (!headers.isEmpty()) {
            newContextOptions.setExtraHTTPHeaders(headers);
        }

        return newContextOptions;
    }

    /**
     * Renders a page on a browser borrowed from the pool, returning the browser once done.
     *
     * Returns the response headers of the page's navigation, the context's cookies for the final URL and the
     * rendered inner HTML of the body.
     *
     * @param postData the encoded body to navigate with POST, or {@code null} to navigate with GET
     * @param postContentType the content type of {@code postData}
     */
    public PageScraperResponse scrapePage(int pageId, String pageURL, String readySelector, int timeoutSec,
                                          Map<String, String> headers, String postData, String postContentType)
            throws IOException {
        Browser.NewContextOptions contextOptions = createContextOptions(headers);

        BrowserInstance browserInstance = browserPool.borrowInstance();
        try {
            return scrapePage(browserInstance, pageId, pageURL, readySelector, timeoutSec, contextOptions,
                    postData, postContentType);
        } finally {
            browserPool.returnInstance(browserInstance);
        }
    }

    /**
     * Scrapes a page in a context of its own, so cookies and headers stay isolated per request while the browser
     * itself is reused. The context is always closed, otherwise contexts would pile up inside a browser that now
     * outlives the page.
     * <p>
     * Redirects need no handling here: {@link Page#navigate} follows them and returns the response
     * of the final page.
     */
    private PageScraperResponse scrapePage(
            BrowserInstance browserInstance,
            int pageId,
            String pageURL,
            String readySelector,
            int timeoutSec,
            Browser.NewContextOptions contextOptions,
            String postData,
            String postContentType
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
            if (postData != null) {
                routeNavigationAsPost(page, postData, postContentType);
            }
            Response response = page.navigate(pageURL);

            // Abort unnecessary awaiting if page response isn't 200 OK
            if (response.status() != 200) {
                throw new IOException(String.format("page: %s, response status %d", pageURL, response.status()));
            }

            try {
                page.waitForSelector(readySelector,
                        new Page.WaitForSelectorOptions().setTimeout(timeoutSec * 1000).setState(WaitForSelectorState.ATTACHED));
            } catch (TimeoutError e) {
                throw new IOException(String.format("Dynamic element search timeout exception, page: %s", pageURL));
            } catch (PlaywrightException e) {
                throw new IOException(String.format("page: %s, playwright exception: %s", pageURL, e.getMessage()));
            }

            String body = page.innerHTML("body");

            Map<String, String> cookies = context.cookies(response.url()).stream()
                    .collect(Collectors.toMap(cookie -> cookie.name, cookie -> cookie.value, (first, duplicate) -> first));
            Map<String, String> headers = response.headers();

            return new PageScraperResponse(pageId, pageURL, headers, cookies, body);
        }
    }

    /**
     * Playwright only navigates with GET, so the page's first navigation request is resumed as a POST with the body.
     * Registered last, so it is tried before the other routes; every other request falls back to them. A redirect
     * after the POST is navigated as usual.
     */
    private void routeNavigationAsPost(Page page, String postData, String postContentType) {
        AtomicBoolean isPostSent = new AtomicBoolean();
        page.route("**/*", route -> {
            Request request = route.request();
            boolean isMainFrameNavigation = request.isNavigationRequest() && request.frame().parentFrame() == null;
            if (!isMainFrameNavigation || !isPostSent.compareAndSet(false, true)) {
                route.fallback();
                return;
            }

            Map<String, String> headers = new HashMap<>(request.headers());
            headers.keySet().removeIf("content-type"::equalsIgnoreCase);
            headers.put("Content-Type", postContentType);

            route.resume(new Route.ResumeOptions()
                    .setMethod("POST")
                    .setPostData(postData)
                    .setHeaders(headers));
        });
    }
}
