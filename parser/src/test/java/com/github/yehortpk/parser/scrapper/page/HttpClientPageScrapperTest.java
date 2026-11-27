package com.github.yehortpk.parser.scrapper.page;

import com.github.yehortpk.parser.models.PageRequestParams;
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

class HttpClientPageScrapperTest {
    private final HttpClientPageScrapper pageScrapper = new HttpClientPageScrapper();
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

    private static PageRequestParams.PageRequestParamsBuilder createParams(String url) {
        return PageRequestParams.builder().pageURL(url).timeoutSec(5);
    }

    @Test
    void ScrapPage_ErrorStatus_ShouldThrowHttpStatusException() {
        String url = registerHandler("/forbidden", exchange -> respond(exchange, 403, "blocked".getBytes()));

        HttpStatusException exception = assertThrows(HttpStatusException.class,
                () -> pageScrapper.scrapPage(createParams(url).build()));

        assertEquals(403, exception.getStatusCode());
    }

    @Test
    void ScrapPage_FormPost_ShouldSendEncodedFormBodyWithContentType() throws IOException {
        String url = registerHandler("/form", respondWithText("ok"));
        Map<String, String> data = new LinkedHashMap<>();
        data.put("query", "java developer");
        data.put("city", "Київ");

        pageScrapper.scrapPage(createParams(url).connectionMethod(Connection.Method.POST).data(data).build());

        assertEquals("POST", lastRequest.method());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", lastRequest.headers().get("content-type"));
        assertEquals("query=java+developer&city=%D0%9A%D0%B8%D1%97%D0%B2", lastRequest.body());
        assertNull(lastRequest.query());
    }

    @Test
    void ScrapPage_PostWithRequestBody_ShouldSendDataAsQueryParams() throws IOException {
        String url = registerHandler("/json", respondWithText("ok"));
        Map<String, String> data = new HashMap<>(Map.of("page", "2"));
        Map<String, String> headers = new HashMap<>(Map.of("Content-Type", "application/json"));

        pageScrapper.scrapPage(createParams(url)
                .connectionMethod(Connection.Method.POST)
                .requestBody("{\"a\":1}")
                .data(data)
                .headers(headers)
                .build());

        assertEquals("{\"a\":1}", lastRequest.body());
        assertEquals("page=2", lastRequest.query());
        assertEquals("application/json", lastRequest.headers().get("content-type"));
    }

    @Test
    void ScrapPage_GetWithSpecialCharactersAndExistingQuery_ShouldAppendEncodedParams() throws IOException {
        String url = registerHandler("/search", respondWithText("ok"));
        Map<String, String> data = new HashMap<>(Map.of("terms[countries][]", "Україна"));

        pageScrapper.scrapPage(createParams(url + "?lang=en").data(data).build());

        assertEquals("lang=en&terms%5Bcountries%5D%5B%5D=%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D0%B0", lastRequest.query());
    }

    @Test
    void ScrapPage_InvalidUrl_ShouldThrowIOException() {
        assertThrows(IOException.class, () -> pageScrapper.scrapPage(createParams("http://127.0.0.1/a b").build()));
    }

    @Test
    void ScrapPage_NoUserAgentConfigured_ShouldSendDefaultBrowserUserAgent() throws IOException {
        String url = registerHandler("/ua", respondWithText("ok"));

        pageScrapper.scrapPage(createParams(url).build());

        assertEquals(HttpClientPageScrapper.DEFAULT_USER_AGENT, lastRequest.headers().get("user-agent"));
    }

    @Test
    void ScrapPage_NoAcceptConfigured_ShouldSendWildcardAccept() throws IOException {
        String url = registerHandler("/accept", respondWithText("ok"));

        pageScrapper.scrapPage(createParams(url).build());

        assertEquals("*/*", lastRequest.headers().get("accept"));
    }

    @Test
    void ScrapPage_UrlWithSurroundingWhitespace_ShouldTrimIt() throws IOException {
        String url = registerHandler("/trimmed", respondWithText("trimmed"));

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(" " + url + " ").build());

        assertEquals("trimmed", response.getBody());
    }

    @Test
    void ScrapPage_UserAgentConfigured_ShouldKeepConfiguredUserAgent() throws IOException {
        String url = registerHandler("/custom-ua", respondWithText("ok"));
        Map<String, String> headers = new HashMap<>(Map.of("user-agent", "custom-agent"));

        pageScrapper.scrapPage(createParams(url).headers(headers).build());

        assertEquals("custom-agent", lastRequest.headers().get("user-agent"));
    }

    @Test
    void ScrapPage_GzipResponse_ShouldDecompressBody() throws IOException {
        byte[] compressed = compressWithGzip("<html>vacancies</html>");
        String url = registerHandler("/gzip", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            respond(exchange, 200, compressed);
        });

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).build());

        assertEquals("gzip", lastRequest.headers().get("accept-encoding"));
        assertEquals("<html>vacancies</html>", response.getBody());
    }

    @Test
    void ScrapPage_ResponseWithNonUtf8Charset_ShouldDecodeWithResponseCharset() throws IOException {
        Charset windows1251 = Charset.forName("windows-1251");
        String url = registerHandler("/cp1251", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=windows-1251");
            respond(exchange, 200, "Вакансії".getBytes(windows1251));
        });

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).build());

        assertEquals("Вакансії", response.getBody());
    }

    @Test
    void ScrapPage_StalledResponse_ShouldThrowTimeout() {
        String url = registerHandler("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThrows(HttpTimeoutException.class, () -> pageScrapper.scrapPage(createParams(url).timeoutSec(1).build()));
    }

    @Test
    void ScrapPage_FollowRedirectsDisabled_ShouldReturnRedirectResponse() throws IOException {
        String target = registerHandler("/target", respondWithText("target"));
        String url = registerHandler("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", target);
            respond(exchange, 302, new byte[0]);
        });

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).followRedirects(false).build());

        assertEquals("", response.getBody());
        assertEquals(target, response.getHeaders().get("Location"));
    }

    @Test
    void ScrapPage_FollowRedirectsEnabled_ShouldReturnTargetBodyAndCookies() throws IOException {
        String target = registerHandler("/final", respondWithText("final"));
        String url = registerHandler("/start", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/");
            exchange.getResponseHeaders().add("Location", target);
            respond(exchange, 302, new byte[0]);
        });

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).build());

        assertEquals("final", response.getBody());
        assertEquals("abc", response.getCookies().get("session"));
    }

    @Test
    void ScrapPage_RepeatedResponseHeader_ShouldJoinValues() throws IOException {
        String url = registerHandler("/headers", exchange -> {
            exchange.getResponseHeaders().add("X-Tag", "a");
            exchange.getResponseHeaders().add("X-Tag", "b");
            respond(exchange, 200, "ok".getBytes());
        });

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).build());

        assertEquals("a, b", response.getHeaders().get("x-tag"));
    }

    @Test
    void ScrapPage_RestrictedHeaderConfigured_ShouldSkipItAndSucceed() throws IOException {
        String url = registerHandler("/restricted", respondWithText("ok"));
        Map<String, String> headers = new HashMap<>(Map.of("Host", "example.com", "Connection", "keep-alive"));

        PageScrapperResponse response = pageScrapper.scrapPage(createParams(url).headers(headers).build());

        assertEquals("ok", response.getBody());
        assertEquals("127.0.0.1:" + server.getAddress().getPort(), lastRequest.headers().get("host"));
    }

    @Test
    void ScrapPage_CookiesConfigured_ShouldSendCookieHeader() throws IOException {
        String url = registerHandler("/cookies", respondWithText("ok"));
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("a", "1");
        cookies.put("b", "2");

        pageScrapper.scrapPage(createParams(url).cookies(cookies).build());

        assertEquals("a=1; b=2", lastRequest.headers().get("cookie"));
    }
}
