package com.pipeline.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** A browser for tests: keeps cookies and the CSRF token the way the real front end does. */
public class TestBrowser {

    private final TestRestTemplate http;
    public final Map<String, String> cookies = new LinkedHashMap<>();
    public final List<String> lastSetCookies = new ArrayList<>();
    public String csrf;

    public TestBrowser(TestRestTemplate http) {
        this.http = http;
    }

    public ResponseEntity<Map<String, Object>> get(String path) {
        return exchange(HttpMethod.GET, path, null, true);
    }

    public ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, true);
    }

    public ResponseEntity<Map<String, Object>> postWithoutCsrf(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, false);
    }

    /** Raw bytes of a GET (images). */
    public ResponseEntity<byte[]> getBytes(String path) {
        HttpHeaders headers = new HttpHeaders();
        applyCookies(headers);
        var response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), byte[].class);
        remember(response.getHeaders());
        return response;
    }

    /** Multipart upload: a file part plus plain text fields. */
    public ResponseEntity<Map<String, Object>> upload(String path, String filename, byte[] content, Map<String, String> fields) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        applyCookies(headers);
        if (csrf != null) {
            headers.set("X-CSRFToken", csrf);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        fields.forEach(body::add);
        var response = http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        remember(response.getHeaders());
        return response;
    }

    /** Opens the site: gets the CSRF token. */
    public TestBrowser open() {
        get("/api/session");
        return this;
    }

    public TestBrowser loginAs(String login, String password) {
        open();
        var response = post("/api/login", Map.of("login", login, "password", password));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return this;
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, Object body, boolean withCsrf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyCookies(headers);
        if (withCsrf && csrf != null) {
            headers.set("X-CSRFToken", csrf);
        }
        var response = http.exchange(path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        remember(response.getHeaders());
        Object token = response.getBody() == null ? null : response.getBody().get("csrfToken");
        if (token instanceof String s) {
            csrf = s;
        }
        return response;
    }

    private void applyCookies(HttpHeaders headers) {
        if (!cookies.isEmpty()) {
            headers.set(HttpHeaders.COOKIE, String.join("; ", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).toList()));
        }
    }

    private void remember(HttpHeaders headers) {
        lastSetCookies.clear();
        for (String header : headers.getOrEmpty(HttpHeaders.SET_COOKIE)) {
            lastSetCookies.add(header);
            String[] nameValue = header.split(";", 2)[0].split("=", 2);
            if (header.contains("Max-Age=0") || nameValue.length < 2 || nameValue[1].isEmpty()) {
                cookies.remove(nameValue[0]);
            } else {
                cookies.put(nameValue[0], nameValue[1]);
            }
        }
    }

    public String setCookieFor(String name) {
        return lastSetCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElse(null);
    }
}
