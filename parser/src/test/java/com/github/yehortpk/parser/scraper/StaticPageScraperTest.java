package com.github.yehortpk.parser.scraper;

import com.github.yehortpk.parser.models.FetchSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticPageScraperTest {
    private final StaticPageScraper pageScraper = new StaticPageScraper();
    private HttpServer server;
    private volatile RecordedRequest lastRequest;

    private record RecordedRequest(String method, String query, Map<String, String> headers, String body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String registerHandler(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            recordRequest(exchange);
            handler.handle(exchange);
        });
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void recordRequest(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(", ", values)));
        lastRequest = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawQuery(),
                headers,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        );
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static HttpHandler respondWithText(String body) {
        return exchange -> respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] compressWithGzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static FetchSettings.FetchSettingsBuilder createParams(String url) {
        return FetchSettings.builder().pageURL(url).timeoutSec(5);
    }

    @Test
    void ScrapePage_ErrorStatus_ShouldThrowHttpStatusException() {
        String url = registerHandler("/forbidden", exchange -> respond(exchange, 403, "blocked".getBytes()));

        HttpStatusException exception = assertThrows(HttpStatusException.class,
                () -> pageScraper.scrapePage(createParams(url).build()));

        assertEquals(403, exception.getStatusCode());
    }

    @Test
    void ScrapePage_FormPost_ShouldSendEncodedFormBodyWithContentType() throws IOException {
        String url = registerHandler("/form", respondWithText("ok"));
        Map<String, String> data = new LinkedHashMap<>();
        data.put("query", "java developer");
        data.put("city", "Київ");

        pageScraper.scrapePage(createParams(url).connectionMethod(Connection.Method.POST).data(data).build());

        assertEquals("POST", lastRequest.method());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", lastRequest.headers().get("content-type"));
        assertEquals("query=java+developer&city=%D0%9A%D0%B8%D1%97%D0%B2", lastRequest.body());
        assertNull(lastRequest.query());
    }

    @Test
    void ScrapePage_PostWithJsonContentType_ShouldSendDataAsJsonBody() throws IOException {
        String url = registerHandler("/json", respondWithText("ok"));
        Map<String, String> data = new LinkedHashMap<>();
        data.put("page", "2");
        data.put("terms[countries][]", "28");

        pageScraper.scrapePage(createParams(url)
                .connectionMethod(Connection.Method.POST)
                .data(data)
                .headers(Map.of("Content-Type", "application/json; charset=UTF-8"))
                .build());

        assertEquals("{\"page\":\"2\",\"terms[countries][]\":\"28\"}", lastRequest.body());
        assertNull(lastRequest.query());
        assertEquals("application/json; charset=UTF-8", lastRequest.headers().get("content-type"));
    }

    @Test
    void ScrapePage_GetWithJsonContentType_ShouldSendDataAsQueryParams() throws IOException {
        String url = registerHandler("/json-get", respondWithText("ok"));

        pageScraper.scrapePage(createParams(url)
                .data(Map.of("onlyData", "true"))
                .headers(Map.of("Content-Type", "application/json"))
                .build());

        assertEquals("GET", lastRequest.method());
        assertEquals("onlyData=true", lastRequest.query());
        assertEquals("", lastRequest.body());
    }

    @Test
    void ScrapePage_AnyPage_ShouldCopyPageIdAndUrlFromSettings() throws IOException {
        String url = registerHandler("/page", respondWithText("body"));

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).pageId(3).build());

        assertEquals(3, response.getPageId());
        assertEquals(url, response.getPageURL());
        assertEquals("body", response.getBody());
    }

    @Test
    void ScrapePage_GetWithSpecialCharactersAndExistingQuery_ShouldAppendEncodedParams() throws IOException {
        String url = registerHandler("/search", respondWithText("ok"));
        Map<String, String> data = new HashMap<>(Map.of("terms[countries][]", "Україна"));

        pageScraper.scrapePage(createParams(url + "?lang=en").data(data).build());

        assertEquals("lang=en&terms%5Bcountries%5D%5B%5D=%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D0%B0", lastRequest.query());
    }

    @Test
    void ScrapePage_InvalidUrl_ShouldThrowIOException() {
        assertThrows(IOException.class, () -> pageScraper.scrapePage(createParams("http://127.0.0.1/a b").build()));
    }

    @Test
    void ScrapePage_NoUserAgentConfigured_ShouldSendDefaultBrowserUserAgent() throws IOException {
        String url = registerHandler("/ua", respondWithText("ok"));

        pageScraper.scrapePage(createParams(url).build());

        assertEquals(StaticPageScraper.DEFAULT_USER_AGENT, lastRequest.headers().get("user-agent"));
    }

    @Test
    void ScrapePage_NoAcceptConfigured_ShouldSendWildcardAccept() throws IOException {
        String url = registerHandler("/accept", respondWithText("ok"));

        pageScraper.scrapePage(createParams(url).build());

        assertEquals("*/*", lastRequest.headers().get("accept"));
    }

    @Test
    void ScrapePage_UrlWithSurroundingWhitespace_ShouldTrimIt() throws IOException {
        String url = registerHandler("/trimmed", respondWithText("trimmed"));

        PageScraperResponse response = pageScraper.scrapePage(createParams(" " + url + " ").build());

        assertEquals("trimmed", response.getBody());
    }

    @Test
    void ScrapePage_UserAgentConfigured_ShouldKeepConfiguredUserAgent() throws IOException {
        String url = registerHandler("/custom-ua", respondWithText("ok"));
        Map<String, String> headers = new HashMap<>(Map.of("user-agent", "custom-agent"));

        pageScraper.scrapePage(createParams(url).headers(headers).build());

        assertEquals("custom-agent", lastRequest.headers().get("user-agent"));
    }

    @Test
    void ScrapePage_GzipResponse_ShouldDecompressBody() throws IOException {
        byte[] compressed = compressWithGzip("<html>vacancies</html>");
        String url = registerHandler("/gzip", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            respond(exchange, 200, compressed);
        });

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).build());

        assertEquals("gzip", lastRequest.headers().get("accept-encoding"));
        assertEquals("<html>vacancies</html>", response.getBody());
    }

    @Test
    void ScrapePage_ResponseWithNonUtf8Charset_ShouldDecodeWithResponseCharset() throws IOException {
        Charset windows1251 = Charset.forName("windows-1251");
        String url = registerHandler("/cp1251", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=windows-1251");
            respond(exchange, 200, "Вакансії".getBytes(windows1251));
        });

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).build());

        assertEquals("Вакансії", response.getBody());
    }

    @Test
    void ScrapePage_StalledResponse_ShouldThrowTimeout() {
        String url = registerHandler("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThrows(HttpTimeoutException.class, () -> pageScraper.scrapePage(createParams(url).timeoutSec(1).build()));
    }

    @Test
    void ScrapePage_FollowRedirectsDisabled_ShouldReturnRedirectResponse() throws IOException {
        String target = registerHandler("/target", respondWithText("target"));
        String url = registerHandler("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", target);
            respond(exchange, 302, new byte[0]);
        });

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).followRedirects(false).build());

        assertEquals("", response.getBody());
        assertEquals(target, response.getHeaders().get("Location"));
    }

    @Test
    void ScrapePage_FollowRedirectsEnabled_ShouldReturnTargetBodyAndCookies() throws IOException {
        String target = registerHandler("/final", respondWithText("final"));
        String url = registerHandler("/start", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/");
            exchange.getResponseHeaders().add("Location", target);
            respond(exchange, 302, new byte[0]);
        });

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).build());

        assertEquals("final", response.getBody());
        assertEquals("abc", response.getCookies().get("session"));
    }

    @Test
    void ScrapePage_RepeatedResponseHeader_ShouldJoinValues() throws IOException {
        String url = registerHandler("/headers", exchange -> {
            exchange.getResponseHeaders().add("X-Tag", "a");
            exchange.getResponseHeaders().add("X-Tag", "b");
            respond(exchange, 200, "ok".getBytes());
        });

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).build());

        assertEquals("a, b", response.getHeaders().get("x-tag"));
    }

    @Test
    void ScrapePage_RestrictedHeaderConfigured_ShouldSkipItAndSucceed() throws IOException {
        String url = registerHandler("/restricted", respondWithText("ok"));
        Map<String, String> headers = new HashMap<>(Map.of("Host", "example.com", "Connection", "keep-alive"));

        PageScraperResponse response = pageScraper.scrapePage(createParams(url).headers(headers).build());

        assertEquals("ok", response.getBody());
        assertEquals("127.0.0.1:" + server.getAddress().getPort(), lastRequest.headers().get("host"));
    }

    @Test
    void ScrapePage_CookiesConfigured_ShouldSendCookieHeader() throws IOException {
        String url = registerHandler("/cookies", respondWithText("ok"));
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "1");
        cookies.put("b", "2");

        pageScraper.scrapePage(createParams(url).cookies(cookies).build());

        assertEquals("a=1; b=2", lastRequest.headers().get("cookie"));
    }
}
