package com.halloween.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "application.rate-limit.requests-per-minute=3")
class RateLimitFilterTest {

    // Production applies Spring Security's StrictHttpFirewall, which rejects ';' in
    // URLs outright with a 400 before any filter runs. This test allows semicolons
    // so the matrix-param variant of a limited path can be exercised end-to-end: the
    // rate limiter must still treat /auth/login;x=N as the same bucket as /auth/login.
    @TestConfiguration
    static class SemicolonPermissiveFirewallConfig {
        @Bean
        org.springframework.security.web.firewall.HttpFirewall allowSemicolonFirewall() {
            org.springframework.security.web.firewall.StrictHttpFirewall firewall =
                    new org.springframework.security.web.firewall.StrictHttpFirewall();
            firewall.setAllowSemicolon(true);
            return firewall;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withinThreshold_passesThroughToAuth() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("10.0.0.1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void login_exceedingThreshold_returns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("10.0.0.2"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("10.0.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }

    @Test
    void refresh_exceedingThreshold_returns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/refresh").with(request -> {
                        request.addHeader("X-Forwarded-For", "10.0.0.3");
                        return request;
                    }))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/auth/refresh").with(request -> {
                    request.addHeader("X-Forwarded-For", "10.0.0.3");
                    return request;
                }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_withMatrixParams_sharesBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginWithMatrixParam("10.0.0.9", i))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginWithMatrixParam("10.0.0.9", 3))
                .andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.RequestBuilder loginWithMatrixParam(String clientIp, int seq) {
        return post("/auth/login;x=" + seq)
                .with(request -> {
                    request.addHeader("X-Forwarded-For", clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"wrongpass\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String clientIp) {
        return post("/auth/login")
                .with(request -> {
                    request.addHeader("X-Forwarded-For", clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"wrongpass\"}");
    }
}
