package com.github.yehortpk.crawler.scraper;

import com.github.yehortpk.crawler.models.FetchSettings;
import com.github.yehortpk.crawler.utils.browser.BrowserPool;
import com.github.yehortpk.crawler.services.CrawlingProgressService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders pages in a real Chromium, so POST support is checked against the navigation request the browser actually
 * sends rather than against Playwright's API.
 */
class DynamicPageScraperTest {
    private static final String READY_SELECTOR = "#ready";
    private static final String PAGE_HTML = "<html><body><div id=\"ready\">rendered</div></body></html>";

    private static BrowserPool browserPool;

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    private record RecordedRequest(String method, String query, String contentType, String cookie, String userAgent,
                                   String body) {}

    @BeforeAll
    static void createBrowserPool() {
        browserPool = new BrowserPool("1", new CrawlingProgressService());
    }

    @AfterAll
    static void closeBrowserPool() {
        browserPool.closeInstances();
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/jobs", this::recordAndRespond);
        server.createContext("/moved", this::redirectToJobs);
        server.createContext("/slow", this::respondAfterDelay);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void recordAndRespond(HttpExchange exchange) throws IOException {
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("Cookie"),
                exchange.getRequestHeaders().getFirst("User-Agent"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        ));

        byte[] body = PAGE_HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void redirectToJobs(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Location", "/jobs");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void respondAfterDelay(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(Duration.ofSeconds(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        recordAndRespond(exchange);
    }

    private String createPageURL() {
        return createPageURL("/jobs");
    }

    private String createPageURL(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static Map<String, String> createData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("page", "2");
        data.put("city", "Київ");
        return data;
    }

    @Test
    void ScrapePage_PostWithoutContentType_ShouldSendDataAsFormBody() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        PageScraperResponse response = pageScraper.scrapePage(FetchSettings.builder()
                .pageId(2)
                .pageURL(createPageURL())
                .connectionMethod(Connection.Method.POST)
                .data(createData())
                .timeoutSec(20)
                .build());

        RecordedRequest request = requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", request.contentType());
        assertEquals("page=2&city=%D0%9A%D0%B8%D1%97%D0%B2", request.body());
        assertNull(request.query());
        assertTrue(response.getBody().contains("rendered"));
        assertEquals(2, response.getPageId());
    }

    @Test
    void ScrapePage_PostWithJsonContentType_ShouldSendDataAsJsonBody() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL())
                .connectionMethod(Connection.Method.POST)
                .headers(Map.of("Content-Type", "application/json"))
                .data(createData())
                .timeoutSec(20)
                .build());

        RecordedRequest request = requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.contentType());
        assertEquals("{\"page\":\"2\",\"city\":\"Київ\"}", request.body());
    }

    @Test
    void ScrapePage_Get_ShouldSendDataAsQueryParams() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL())
                .data(Map.of("page", "2"))
                .timeoutSec(20)
                .build());

        RecordedRequest request = requests.getFirst();
        assertEquals("GET", request.method());
        assertEquals("page=2", request.query());
    }

    @Test
    void ScrapePage_GetWithQueryInURL_ShouldAppendEncodedData() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL("/jobs?sort=new"))
                .data(Map.of("city", "Київ"))
                .timeoutSec(20)
                .build());

        assertEquals("sort=new&city=%D0%9A%D0%B8%D1%97%D0%B2", requests.getFirst().query());
    }

    @Test
    void ScrapePage_Cookies_ShouldSendCookies() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL())
                .cookies(Map.of("session", "abc"))
                .timeoutSec(20)
                .build());

        assertEquals("session=abc", requests.getFirst().cookie());
    }

    @Test
    void ScrapePage_UserAgentHeader_ShouldSendGivenUserAgent() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL())
                .headers(Map.of("user-agent", "VacancyVaultTest/1.0"))
                .timeoutSec(20)
                .build());

        assertEquals("VacancyVaultTest/1.0", requests.getFirst().userAgent());
    }

    @Test
    void ScrapePage_RedirectWithFollowRedirects_ShouldRenderTarget() throws IOException {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        PageScraperResponse response = pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL("/moved"))
                .timeoutSec(20)
                .build());

        assertTrue(response.getBody().contains("rendered"));
    }

    @Test
    void ScrapePage_RedirectWithoutFollowRedirects_ShouldFail() {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        assertThrows(IOException.class, () -> pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL("/moved"))
                .followRedirects(false)
                .timeoutSec(20)
                .build()));
    }

    @Test
    void ScrapePage_NavigationSlowerThanTimeout_ShouldFail() {
        DynamicPageScraper pageScraper = new DynamicPageScraper(browserPool, READY_SELECTOR);

        assertThrows(IOException.class, () -> pageScraper.scrapePage(FetchSettings.builder()
                .pageURL(createPageURL("/slow"))
                .timeoutSec(1)
                .build()));
    }
}
