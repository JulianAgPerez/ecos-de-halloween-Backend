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
@TestPropertySource(properties = {
        "application.rate-limit.requests-per-minute=3",
        "application.rate-limit.trusted-proxies=10.0.0.0/8"
})
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

    @Test
    void login_withSpoofedXffFromUntrustedPeer_ignoresHeader() throws Exception {
        // 203.0.113.0/24 is not configured as a trusted proxy, and the direct peer
        // (MockMvc default 127.0.0.1) is not trusted either, so X-Forwarded-For is
        // ignored and every request buckets on the remote address. Rotating XFF
        // therefore cannot dodge the limiter: the 4th request within the window
        // must be rejected regardless of the spoofed header.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(spoofedLogin("203.0.113." + (i + 1)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(spoofedLogin("203.0.113.200"))
                .andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.RequestBuilder loginWithMatrixParam(String clientIp, int seq) {
        return post("/auth/login;x=" + seq)
                .with(request -> {
                    // MockMvc defaults the remote address to 127.0.0.1. These tests model a
                    // trusted proxy (10.0.0.0/8) that sets XFF, so the direct peer must be that
                    // trusted address for the filter to honor the header, as it does in
                    // production behind the trusted LB.
                    request.setRemoteAddr(clientIp);
                    request.addHeader("X-Forwarded-For", clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"wrongpass\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder spoofedLogin(String clientIp) {
        return post("/auth/login")
                .with(request -> {
                    // Deliberately leave the remote address at the MockMvc default
                    // (127.0.0.1, not a trusted proxy): X-Forwarded-For must be ignored.
                    request.addHeader("X-Forwarded-For", clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"wrongpass\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String clientIp) {
        return post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    request.addHeader("X-Forwarded-For", clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"wrongpass\"}");
    }
}
