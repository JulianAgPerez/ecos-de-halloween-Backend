package com.halloween.classic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikisourceClientTest {

    private static final String PARSE_JSON =
            "{\"parse\":{\"text\":{\"*\":\"<p>Era un gato.</p>\"}}}";

    private MockRestServiceServer server;
    private WikisourceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Tiny backoff keeps the retry tests fast.
        client = new WikisourceClient(builder.build(), 10);
    }

    @Test
    void fetchPageHtml_retriesAfterConnectionFailure_andReturnsHtml() {
        server.expect(once(), requestTo(containsString("/w/api.php")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("connection reset")));
        expectSuccessfulParseRequest();

        String html = client.fetchPageHtml("El gato negro");

        assertThat(html).isEqualTo("<p>Era un gato.</p>");
        server.verify();
    }

    @Test
    void fetchPageHtml_retriesAfter5xx_andReturnsHtml() {
        server.expect(once(), requestTo(containsString("/w/api.php")))
                .andRespond(withServerError());
        expectSuccessfulParseRequest();

        String html = client.fetchPageHtml("El gato negro");

        assertThat(html).isEqualTo("<p>Era un gato.</p>");
        server.verify();
    }

    @Test
    void fetchPageHtml_givesUpWith502_whenEveryAttemptFails() {
        server.expect(times(WikisourceClient.MAX_ATTEMPTS), requestTo(containsString("/w/api.php")))
                .andRespond(withException(new IOException("read timeout")));

        assertThatThrownBy(() -> client.fetchPageHtml("El gato negro"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        server.verify();
    }

    @Test
    void fetchPageHtml_missingTitle_returns404WithoutRetry() {
        server.expect(once(), requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{\"error\":{\"code\":\"missingtitle\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPageHtml("no-existe"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);

        server.verify();
    }

    private void expectSuccessfulParseRequest() {
        server.expect(once(), requestTo(containsString("/w/api.php")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(PARSE_JSON, MediaType.APPLICATION_JSON));
    }
}
