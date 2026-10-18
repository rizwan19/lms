package com.saasresearcher.learning.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal browser-like JSON client: keeps cookies and echoes the CSRF cookie as a header. */
public class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public ApiResponse get(String path, String... headers) {
        return send(request(path, headers).GET());
    }

    public ApiResponse post(String path, Object body, String... headers) {
        return send(request(path, headers).POST(json(body)).header("Content-Type", "application/json"));
    }

    public ApiResponse put(String path, Object body, String... headers) {
        return send(request(path, headers).PUT(json(body)).header("Content-Type", "application/json"));
    }

    public ApiResponse delete(String path) {
        return send(request(path).DELETE());
    }

    public ApiResponse postBytes(String path, byte[] body, String contentType) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofByteArray(body)).header("Content-Type", contentType));
    }

    /** Streams a generated body with chunked transfer encoding (no Content-Length, nothing buffered). */
    public ApiResponse postStream(String path, java.util.function.Supplier<java.io.InputStream> body, String contentType) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofInputStream(body)).header("Content-Type", contentType));
    }

    public ApiResponse send(HttpRequest.Builder builder) {
        try {
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new ApiResponse(response.statusCode(), response.body(), response.headers().map());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (headers.length > 0) {
            builder.headers(headers);
        }
        csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        return builder;
    }

    public java.util.Optional<String> csrfToken() {
        return cookie("XSRF-TOKEN");
    }

    public java.util.Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    public void clearCookie(String name) {
        cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .toList()
                .forEach(cookie -> cookies.getCookieStore().remove(null, cookie));
    }

    private static HttpRequest.BodyPublisher json(Object body) {
        return HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body == null ? Map.of() : body));
    }

    public record ApiResponse(int status, byte[] bytes, Map<String, java.util.List<String>> headers) {

        public JsonNode json() {
            return JSON.readTree(bytes);
        }

        public String text() {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }

        public String code() {
            return json().path("code").asString();
        }

        /** Fails fast with the response body when the status is unexpected. */
        public ApiResponse expect(int expectedStatus) {
            if (status != expectedStatus) {
                throw new AssertionError("Expected HTTP " + expectedStatus + " but was " + status + ": " + text());
            }
            return this;
        }
    }
}
