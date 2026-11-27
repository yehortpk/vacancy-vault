package com.github.yehortpk.parser.scrapper.page;

import com.github.yehortpk.parser.models.PageRequestParams;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Default static pages scrapper, based on {@link java.net.http.HttpClient}. Mirrors the request semantics of
 * {@link JsoupPageScrapper} (status checks, default headers, query/body serialisation, gzip, charset), without
 * Jsoup's 2MB response body limit.
 */
@Slf4j
public class HttpClientPageScrapper implements PageScrapper {
    /**
     * Sent when no User-Agent is configured. Keep it a recent browser version: some WAFs reject outdated ones, such as Jsoup's Chrome 79 default
     */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final String USER_AGENT = "User-Agent";
    private static final String ACCEPT = "Accept";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded; charset=UTF-8";
    /**
     * Headers {@link HttpClient} refuses to set, unless the JVM is started with
     * {@code -Djdk.httpclient.allowRestrictedHeaders}. The client computes them itself.
     */
    private static final Set<String> RESTRICTED_HEADERS = Set.of("connection", "content-length", "expect", "host", "upgrade");

    @Override
    public PageScrapperResponse scrapPage(PageRequestParams pageRequestParams) throws IOException {
        HttpRequest request = buildRequest(pageRequestParams);

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(pageRequestParams.getTimeoutSec()))
                .followRedirects(pageRequestParams.isFollowRedirects() ? HttpClient.Redirect.ALWAYS : HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);

        // Manage cookies for this request/response cycle, including the ones set during redirects
        CookieManager cookieManager = new CookieManager();
        clientBuilder.cookieHandler(cookieManager);

        Proxy proxy = pageRequestParams.getProxy();
        if (proxy != null) {
            clientBuilder.proxy(ProxySelector.of((InetSocketAddress) proxy.address()));
        }

        log.info("Connecting to the page {}, method: {}, proxy: {},  data: {}, headers: {}, cookies: {}",
                pageRequestParams.getPageURL(),
                pageRequestParams.getConnectionMethod(),
                pageRequestParams.getProxy(),
                pageRequestParams.getData(),
                pageRequestParams.getHeaders(),
                pageRequestParams.getCookies()
        );

        try (HttpClient client = clientBuilder.build()) {
            HttpResponse<byte[]> response = sendRequest(client, request, pageRequestParams.getTimeoutSec());

            log.info("Connection to the page {}, method: {}, proxy: {},  data: {} was established",
                    pageRequestParams.getPageURL(),
                    pageRequestParams.getConnectionMethod(),
                    pageRequestParams.getProxy(),
                    pageRequestParams.getData()
            );

            int status = response.statusCode();
            if (status < 200 || status >= 400) {
                throw new HttpStatusException("HTTP error fetching URL", status, response.uri().toString());
            }

            Map<String, String> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, List<String>> es : response.headers().map().entrySet()) {
                responseHeaders.put(es.getKey(), String.join(", ", es.getValue()));
            }

            Map<String, String> responseCookies = new HashMap<>();
            for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
                responseCookies.put(cookie.getName(), cookie.getValue());
            }

            return new PageScrapperResponse(responseHeaders, responseCookies, decodeBody(response));
        }
    }

    /**
     * Sends the request, bounding the whole exchange (connect, headers and body) by the timeout, like Jsoup does.
     * On timeout or interruption the client is shut down so the in-flight exchange is aborted.
     */
    private HttpResponse<byte[]> sendRequest(HttpClient client, HttpRequest request, int timeoutSec) throws IOException {
        CompletableFuture<HttpResponse<byte[]>> responseFuture =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return responseFuture.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            client.shutdownNow();
            throw new HttpTimeoutException("Request to " + request.uri() + " timed out after " + timeoutSec + "s");
        } catch (InterruptedException e) {
            client.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Request to " + request.uri() + " was interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(e.getCause());
        }
    }

    private HttpRequest buildRequest(PageRequestParams pageRequestParams) throws IOException {
        Map<String, String> data = pageRequestParams.getData();
        String requestBody = pageRequestParams.getRequestBody();
        boolean isPost = pageRequestParams.getConnectionMethod() == Connection.Method.POST;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(pageRequestParams.getTimeoutSec()));

        // Like Jsoup: data goes to the query string, unless it is a form POST, where it becomes the body
        if (isPost) {
            String body = requestBody != null ? requestBody : encodeParams(data);
            String url = requestBody != null ? appendQueryParams(pageRequestParams.getPageURL(), data) : pageRequestParams.getPageURL();
            requestBuilder.uri(createURI(url)).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            requestBuilder.uri(createURI(appendQueryParams(pageRequestParams.getPageURL(), data))).GET();
        }

        Map<String, String> headers = pageRequestParams.getHeaders();
        headers.forEach((name, value) -> {
            if (RESTRICTED_HEADERS.contains(name.toLowerCase())) {
                log.warn("Header {} can't be set by HttpClient and is skipped, page: {}", name, pageRequestParams.getPageURL());
            } else {
                requestBuilder.header(name, value);
            }
        });

        if (!hasHeader(headers, USER_AGENT)) {
            requestBuilder.header(USER_AGENT, DEFAULT_USER_AGENT);
        }
        // HttpClient sends no Accept by default; some servers pick the
        // response format from it, so send */* like Jsoup does
        if (!hasHeader(headers, ACCEPT)) {
            requestBuilder.header(ACCEPT, "*/*");
        }
        if (!hasHeader(headers, ACCEPT_ENCODING)) {
            requestBuilder.header(ACCEPT_ENCODING, "gzip");
        }
        if (isPost && !hasHeader(headers, CONTENT_TYPE)) {
            requestBuilder.header(CONTENT_TYPE, FORM_URL_ENCODED);
        }

        convertCookiesToHeader(pageRequestParams.getCookies())
                .ifPresent(cookies -> requestBuilder.header("Cookie", cookies));

        return requestBuilder.build();
    }

    private URI createURI(String url) throws MalformedURLException {
        try {
            // Configured URLs may carry stray whitespace
            return URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            MalformedURLException malformedURLException = new MalformedURLException("Invalid page URL: " + url);
            malformedURLException.initCause(e);
            throw malformedURLException;
        }
    }

    /**
     * Decompresses the body according to Content-Encoding and decodes it with the charset from Content-Type,
     * falling back to UTF-8
     */
    private String decodeBody(HttpResponse<byte[]> response) throws IOException {
        byte[] bytes = response.body();
        Optional<String> contentEncoding = response.headers().firstValue(CONTENT_ENCODING).map(String::toLowerCase);

        if (contentEncoding.isPresent() && bytes.length > 0) {
            InputStream decodedStream = switch (contentEncoding.get()) {
                case "gzip" -> new GZIPInputStream(new ByteArrayInputStream(bytes));
                case "deflate" -> new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(true));
                default -> null;
            };
            if (decodedStream != null) {
                try (decodedStream) {
                    bytes = decodedStream.readAllBytes();
                }
            }
        }

        Charset charset = response.headers().firstValue(CONTENT_TYPE)
                .flatMap(this::findCharset)
                .orElse(StandardCharsets.UTF_8);
        return new String(bytes, charset);
    }

    private Optional<Charset> findCharset(String contentType) {
        try {
            return Optional.ofNullable(MediaType.parseMediaType(contentType).getCharset());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean hasHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    private Optional<String> convertCookiesToHeader(Map<String, String> cookies) {
        if (cookies.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; ")));
    }

    private String encodeParams(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /**
     * Appends URL-encoded params to the URL, keeping its existing query string if there is one
     * @param pageUrl base url, with or without query params
     * @param params params to append
     * @return final url
     */
    private String appendQueryParams(String pageUrl, Map<String, String> params) {
        if (params.isEmpty()) {
            return pageUrl;
        }

        String separator = pageUrl.contains("?") ? "&" : "?";
        return pageUrl + separator + encodeParams(params);
    }
}
