package com.github.yehortpk.parser.scraper.page;

import com.github.yehortpk.parser.models.FetchSettings;
import com.github.yehortpk.parser.scraper.browser.BrowserPool;
import com.github.yehortpk.parser.services.ParsingProgressService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private record RecordedRequest(String method, String query, String contentType, String body) {}

    @BeforeAll
    static void createBrowserPool() {
        browserPool = new BrowserPool("1", new ParsingProgressService());
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
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        ));

        byte[] body = PAGE_HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String createPageURL() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jobs";
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
        assertEquals("page=2&", request.query());
    }
}
