package com.halloween.service;

import com.halloween.entities.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItZWNvcy1kZS1oYWxsb3dlZW4tYXVkaXQtMjAyNi0wMTIzNDU2Nzg5";
    private static final long JWT_EXPIRATION = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 604_800_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", JWT_EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", REFRESH_EXPIRATION);
    }

    private User user() {
        return User.builder()
                .id(1L)
                .name("Admin")
                .email("admin@test.com")
                .password("encoded")
                .build();
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = jwtService.generateToken(user());
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@test.com");
    }

    @Test
    void generateToken_usesAccessExpiration() {
        String token = jwtService.generateToken(user());
        long lifetime = expiration(token).getTime() - issuedAt(token).getTime();
        assertThat(lifetime).isEqualTo(JWT_EXPIRATION);
    }

    @Test
    void generateRefreshToken_usesRefreshExpiration() {
        String token = jwtService.generateRefreshToken(user());
        long lifetime = expiration(token).getTime() - issuedAt(token).getTime();
        assertThat(lifetime).isEqualTo(REFRESH_EXPIRATION);
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtService.generateToken(user());
        assertThat(jwtService.isTokenValid(token, user())).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUser() {
        User other = User.builder().id(2L).email("other@test.com").build();
        String token = jwtService.generateToken(user());
        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        String token = Jwts.builder()
                .subject("admin@test.com")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
        assertThat(jwtService.isTokenValid(token, user())).isFalse();
    }

    private Date expiration(String token) {
        return claims(token).getExpiration();
    }

    private Date issuedAt(String token) {
        return claims(token).getIssuedAt();
    }

    private io.jsonwebtoken.Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
