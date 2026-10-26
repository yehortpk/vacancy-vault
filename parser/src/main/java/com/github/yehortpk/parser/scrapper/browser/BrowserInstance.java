package com.github.yehortpk.parser.scrapper.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single Playwright driver connection together with the Chromium instance it launched.
 * <p>
 * Playwright's Java client is single-threaded: every call pumps the shared driver pipe until its own
 * reply arrives, dispatching whatever messages it reads along the way. Two threads calling into one
 * instance therefore steal each other's replies. An instance must be used by one thread at a time,
 * which {@link BrowserPool} guarantees by handing it out exclusively.
 */
@Slf4j
public class BrowserInstance implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;

    public BrowserInstance() {
        Map<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright");
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
        browser = playwright.chromium().launch(
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
    }

    public BrowserContext createContext(Browser.NewContextOptions contextOptions) {
        return browser.newContext(contextOptions);
    }

    /**
     * Whether the underlying Chromium is still usable. A crashed browser cannot be recovered, so the
     * pool replaces the whole instance instead of returning it.
     */
    public boolean isConnected() {
        return browser.isConnected();
    }

    @Override
    public void close() {
        try {
            browser.close();
        } catch (RuntimeException e) {
            log.warn("Browser close failed: {}", e.getMessage());
        }
        try {
            playwright.close();
        } catch (RuntimeException e) {
            log.warn("Playwright close failed: {}", e.getMessage());
        }
    }
}
