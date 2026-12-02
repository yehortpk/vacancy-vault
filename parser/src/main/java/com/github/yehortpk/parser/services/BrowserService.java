package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.utils.browser.BrowserInstance;
import com.github.yehortpk.parser.utils.browser.BrowserPool;
import com.github.yehortpk.parser.scraper.PageScraperResponse;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BrowserService {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final String STEALTH_INIT_SCRIPT =
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined }); " +
            "Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] }); " +
            "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] }); " +
            "window.chrome = { runtime: {} };";

    private final BrowserPool browserPool;

    /**
     * A {@code User-Agent} among the headers replaces the default one. It is set on the context rather than sent as
     * an extra header, so {@code navigator.userAgent} reports it too.
     */
    private Browser.NewContextOptions createContextOptions(Map<String, String> headers) {
        Map<String, String> extraHeaders = new HashMap<>(headers);
        String userAgent = headers.entrySet().stream()
                .filter(header -> USER_AGENT_HEADER.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(USER_AGENT);
        extraHeaders.keySet().removeIf(USER_AGENT_HEADER::equalsIgnoreCase);

        Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions()
                .setUserAgent(userAgent)
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Europe/Kyiv");

        if (!extraHeaders.isEmpty()) {
            newContextOptions.setExtraHTTPHeaders(extraHeaders);
        }

        return newContextOptions;
    }

    private List<Cookie> createCookies(String pageURL, Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(cookie -> new Cookie(cookie.getKey(), cookie.getValue()).setUrl(pageURL))
                .toList();
    }

    /**
     * Renders a page on a browser borrowed from the pool, returning the browser once done.
     *
     * Returns the response headers of the page's navigation, the context's cookies for the final URL and the
     * rendered inner HTML of the body.
     *
     * @param timeoutSec bounds the navigation and, separately, the wait for {@code readySelector}
     * @param followRedirects when {@code false}, a page that redirects fails instead of being rendered
     * @param postData the encoded body to navigate with POST, or {@code null} to navigate with GET
     * @param postContentType the content type of {@code postData}
     */
    public PageScraperResponse scrapePage(int pageId, String pageURL, String readySelector, int timeoutSec,
                                          boolean followRedirects, Map<String, String> headers,
                                          Map<String, String> cookies, String postData, String postContentType)
            throws IOException {
        Browser.NewContextOptions contextOptions = createContextOptions(headers);

        BrowserInstance browserInstance = browserPool.borrowInstance();
        try {
            return scrapePage(browserInstance, pageId, pageURL, readySelector, timeoutSec, followRedirects,
                    contextOptions, cookies, postData, postContentType);
        } finally {
            browserPool.returnInstance(browserInstance);
        }
    }

    /**
     * Scrapes a page in a context of its own, so cookies and headers stay isolated per request while the browser
     * itself is reused. The context is always closed, otherwise contexts would pile up inside a browser that now
     * outlives the page.
     * <p>
     * {@link Page#navigate} always follows redirects and returns the response of the final page. A redirect can't be
     * stopped in the browser, so with {@code followRedirects} off it is detected once the navigation is done.
     */
    private PageScraperResponse scrapePage(
            BrowserInstance browserInstance,
            int pageId,
            String pageURL,
            String readySelector,
            int timeoutSec,
            boolean followRedirects,
            Browser.NewContextOptions contextOptions,
            Map<String, String> cookies,
            String postData,
            String postContentType
    ) throws IOException {
        try (BrowserContext context = browserInstance.createContext(contextOptions);
             Page page = context.newPage()
        ) {
            if (!cookies.isEmpty()) {
                context.addCookies(createCookies(pageURL, cookies));
            }
            page.addInitScript(STEALTH_INIT_SCRIPT);

            page.route("**/*.{png,jpg,jpeg,gif,webp}", Route::abort); // Block images
            page.route("**/*.css", Route::abort); // Block stylesheets
            page.route("**/*.woff2", Route::abort); // Block web fonts
            page.route("**/ads*", Route::abort); // Block ads

            page.route("**/*", Route::resume);
            if (postData != null) {
                routeNavigationAsPost(page, postData, postContentType);
            }
            Response response = navigate(page, pageURL, timeoutSec);

            if (!followRedirects && response.request().redirectedFrom() != null) {
                throw new IOException(String.format("page: %s, redirected to %s while redirects are off",
                        pageURL, response.url()));
            }

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

            Map<String, String> responseCookies = context.cookies(response.url()).stream()
                    .collect(Collectors.toMap(cookie -> cookie.name, cookie -> cookie.value, (first, duplicate) -> first));
            Map<String, String> responseHeaders = response.headers();

            return new PageScraperResponse(pageId, pageURL, responseHeaders, responseCookies, body);
        }
    }

    private Response navigate(Page page, String pageURL, int timeoutSec) throws IOException {
        Response response;
        try {
            response = page.navigate(pageURL, new Page.NavigateOptions().setTimeout(timeoutSec * 1000));
        } catch (TimeoutError e) {
            throw new IOException(String.format("page: %s, navigation timed out after %ds", pageURL, timeoutSec));
        } catch (PlaywrightException e) {
            throw new IOException(String.format("page: %s, playwright exception: %s", pageURL, e.getMessage()));
        }

        // Playwright returns no response for a navigation to the same document, such as an anchor change
        if (response == null) {
            throw new IOException(String.format("page: %s, navigation returned no response", pageURL));
        }
        return response;
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
