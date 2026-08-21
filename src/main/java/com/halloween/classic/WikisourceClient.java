package com.halloween.classic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class WikisourceClient {

    private static final Logger log = LoggerFactory.getLogger(WikisourceClient.class);

    // Initial attempt plus retries so a single network blip or 5xx does not
    // surface as an immediate 502. API-level errors (e.g. missingtitle) still fail fast.
    static final int MAX_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_BACKOFF_MILLIS = 250;

    private final RestClient restClient;
    private final long retryBackoffMillis;

    @Autowired
    public WikisourceClient(@Value("${wikisource.api.base-url}") String baseUrl,
                            @Value("${wikisource.user-agent}") String userAgent) {
        this(defaultRestClient(baseUrl, userAgent), DEFAULT_RETRY_BACKOFF_MILLIS);
    }

    WikisourceClient(RestClient restClient, long retryBackoffMillis) {
        this.restClient = restClient;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    private static RestClient defaultRestClient(String baseUrl, String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    public String fetchPageHtml(String pageTitle) {
        ApiResponse response;
        try {
            response = fetchWithRetry(pageTitle);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Error al consultar Wikisource", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al consultar Wikisource");
        }

        if (response == null || response.error != null) {
            if (response != null && response.error != null && "missingtitle".equals(response.error.code)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Página no encontrada en Wikisource");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al consultar Wikisource");
        }

        String html = response.parse != null && response.parse.text != null
                ? response.parse.text.get("*")
                : null;
        if (html == null || html.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Contenido vacío desde Wikisource");
        }
        return html;
    }

    private ApiResponse fetchWithRetry(String pageTitle) {
        RuntimeException lastTransientFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return executeParse(pageTitle);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                lastTransientFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    long delayMillis = retryBackoffMillis << (attempt - 1);
                    log.warn("Transient failure calling Wikisource (attempt {}/{}), retrying in {} ms: {}",
                            attempt, MAX_ATTEMPTS, delayMillis, e.getMessage());
                    sleepBeforeRetry(delayMillis);
                }
            }
        }
        log.warn("Wikisource unreachable after {} attempts", MAX_ATTEMPTS, lastTransientFailure);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Wikisource");
    }

    private ApiResponse executeParse(String pageTitle) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/w/api.php")
                        .queryParam("action", "parse")
                        .queryParam("page", pageTitle)
                        .queryParam("prop", "text")
                        .queryParam("format", "json")
                        .queryParam("redirects", "1")
                        .queryParam("disabletoc", "1")
                        .queryParam("disableeditsection", "1")
                        .build())
                .retrieve()
                .body(ApiResponse.class);
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Wikisource");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse {
        public Parse parse;
        public ApiError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Parse {
        public Map<String, String> text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiError {
        public String code;
        public String info;
    }
}
