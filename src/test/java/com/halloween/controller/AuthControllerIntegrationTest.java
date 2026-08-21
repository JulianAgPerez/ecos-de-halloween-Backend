package com.halloween.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halloween.entities.User;
import com.halloween.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .name("Admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("plainpass"))
                .build());
    }

    @Test
    void login_withValidCredentials_returnsTokens() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"plainpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_publicRegistrationIsDisabled_returns401() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Otro","email":"otro@test.com","password":"pass"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByEmail("otro@test.com")).isEmpty();
    }

    @Test
    void refresh_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"plainpass"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + loginBody.get("refresh_token").asText()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(refreshBody.get("access_token").asText()).isNotEmpty();
    }

    @Test
    void refresh_withAccessToken_returns401() throws Exception {
        JsonNode loginBody = login();

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + loginBody.get("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesTokens_oldRefreshTokenNoLongerUsable() throws Exception {
        JsonNode loginBody = login();
        String oldRefresh = loginBody.get("refresh_token").asText();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + oldRefresh))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(refreshBody.get("refresh_token").asText()).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + oldRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withRevokedRefreshToken_returns401() throws Exception {
        JsonNode firstLogin = login();
        login();

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + firstLogin.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesSubsequentRequests() throws Exception {
        JsonNode loginBody = login();
        String accessToken = loginBody.get("access_token").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/stories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + loginBody.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityChain_isStateless_noSessionCookieIssued() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"plainpass"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(loginResult.getRequest().getSession(false)).isNull();
        assertThat(loginResult.getResponse().getCookie("JSESSIONID")).isNull();

        MvcResult deniedResult = mockMvc.perform(post("/api/stories"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(deniedResult.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    void login_withBlankCredentials_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void login_withInvalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"plainpass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void login_withMalformedJson_returns400WithGenericBody() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void refresh_withoutAuthorizationHeader_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_withGarbageBearerToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withExpiredRefreshToken_returns401() throws Exception {
        String expiredRefresh = Jwts.builder()
                .subject("admin@test.com")
                .claim("type", "REFRESH")
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 10_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 5_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItZWNvcy1kZS1oYWxsb3dlZW4tYXVkaXQtMjAyNi0wMTIzNDU2Nzg5")))
                .compact();

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + expiredRefresh))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"plainpass"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString());
    }
}
