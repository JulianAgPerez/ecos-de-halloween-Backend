package com.halloween.service;

import com.halloween.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    private static final String TYPE_CLAIM = "type";
    private static final int MIN_SECRET_KEY_BYTES = 32;

    @Value("${application.secret-key}")
    private String secretKey;
    @Value("${application.expiration}")
    private long jwtExpiration;
    @Value("${application.refresh-expiration}")
    private long refreshExpiration;

    @PostConstruct
    void validateSecretKey() {
        final byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < MIN_SECRET_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT secret key is too weak: decoded length is " + keyBytes.length
                            + " bytes, but HS256 requires at least " + MIN_SECRET_KEY_BYTES + " bytes");
        }
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String generateToken(final User user) {
        return buildToken(user, jwtExpiration, TokenType.ACCESS);
    }

    public String generateRefreshToken(final User user) {
        return buildToken(user, refreshExpiration, TokenType.REFRESH);
    }

    private String buildToken(final User user, final long expiration, final TokenType type) {
        return Jwts
                .builder()
                .id(UUID.randomUUID().toString())
                .claims(Map.of("name", user.getName(), TYPE_CLAIM, type.name()))
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isRefreshToken(final String token) {
        try {
            final String type = parseClaims(token).get(TYPE_CLAIM, String.class);
            return TokenType.REFRESH.name().equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, User user) {
        try {
            final String username = extractUsername(token);
            return (username.equals(user.getEmail())) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    private SecretKey getSignInKey() {
        final byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}