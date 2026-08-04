package com.halloween.classic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class WikisourceClient {

    private static final Logger log = LoggerFactory.getLogger(WikisourceClient.class);

    private final RestClient restClient;

    public WikisourceClient(@Value("${wikisource.api.base-url}") String baseUrl,
                            @Value("${wikisource.user-agent}") String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    public String fetchPageHtml(String pageTitle) {
        ApiResponse response;
        try {
            response = restClient.get()
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
        } catch (ResourceAccessException e) {
            log.error("No se pudo conectar con Wikisource", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Wikisource");
        } catch (Exception e) {
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
