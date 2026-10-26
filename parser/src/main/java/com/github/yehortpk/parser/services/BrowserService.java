package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BrowserService {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    private static final String STEALTH_INIT_SCRIPT =
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined }); " +
            "Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] }); " +
            "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] }); " +
            "window.chrome = { runtime: {} };";

    private Browser.NewPageOptions createPageOptions(Map<String, String> headers, String proxy) {
        Browser.NewPageOptions newPageOptions = new Browser.NewPageOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Europe/Kyiv");

        if (!headers.isEmpty()) {
            newPageOptions.setExtraHTTPHeaders(headers);
        }

        if (proxy != null) {
            newPageOptions.setProxy(proxy);
        }

        return newPageOptions;
    }


    public PageScrapperResponse scrapPage(String pageURL, String dynamicQuerySelector, int timeout, Map<String, String> headers, String proxy) throws IOException {
        Browser.NewPageOptions pageOptions = createPageOptions(headers, proxy);

        return scrapPage(pageURL, dynamicQuerySelector, timeout, pageOptions);
    }

    private PageScrapperResponse scrapPage(String pageURL, String dynamicQuerySelector, int timeout, Browser.NewPageOptions pageOptions) throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright");
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        Playwright.CreateOptions options = new Playwright.CreateOptions().setEnv(env);
        try (Playwright playwright = Playwright.create(options);
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(
                                    List.of(
                                            "--disable-gpu",
                                            "--disable-dev-shm-usage",
                                            "--no-sandbox",
                                            "--blink-settings=imagesEnabled=false",
                                            "--disable-blink-features=AutomationControlled"
                                    )
                            )
            );

            Page page = browser.newPage(pageOptions)
        ) {
            page.addInitScript(STEALTH_INIT_SCRIPT);

            page.route("**/*.{png,jpg,jpeg,gif,webp}", Route::abort); // Block images
            page.route("**/*.css", Route::abort); // Block stylesheets
            page.route("**/*.woff2", Route::abort); // Block web fonts
            page.route("**/ads*", Route::abort); // Block ads

            page.route("**/*", Route::resume);
            Response response = page.navigate(pageURL);

            // Handle page redirects
            if (response.status() == 301) {
                return scrapPage(response.headerValue("Location"), dynamicQuerySelector, timeout, pageOptions);
            }

            // Abort unnecessary awaiting if page response isn't 200 OK
            if (response.status() != 200) {
                throw new IOException(String.format("page: %s, 403 exception", pageURL));
            }

            page.waitForLoadState(LoadState.NETWORKIDLE);

            try {
                page.waitForSelector(dynamicQuerySelector,
                        new Page.WaitForSelectorOptions().setTimeout(timeout * 1000).setState(WaitForSelectorState.ATTACHED));
            } catch (TimeoutError e) {
                throw new IOException(String.format("Dynamic element search timeout exception, page: %s", pageURL));
            } catch (PlaywrightException e) {
                throw new IOException(String.format("page: %s, playwright exception: %s", pageURL, e.getMessage()));
            }

            String body = page.innerHTML("body");

            Map<String, String> cookies = page.context().cookies(response.url()).stream()
                    .collect(Collectors.toMap(cookie -> cookie.name, cookie -> cookie.value));
            Map<String, String> headers = response.headers();

            return new PageScrapperResponse(headers, cookies, body);
        }
    }
}
