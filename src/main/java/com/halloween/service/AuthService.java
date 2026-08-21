package com.halloween.service;

import com.halloween.entities.User;
import com.halloween.controller.auth.RegisterRequest;
import com.halloween.controller.auth.TokenResponse;
import com.halloween.repository.Token;
import com.halloween.repository.TokenRepository;
import com.halloween.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository repository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenResponse register(final RegisterRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        final User user = User.builder()
                .name(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        final User savedUser = repository.save(user);
        final String jwtToken = jwtService.generateToken(savedUser);
        final String refreshToken = jwtService.generateRefreshToken(savedUser);

        saveUserToken(savedUser, jwtToken);
        saveUserToken(savedUser, refreshToken);
        return new TokenResponse(jwtToken, refreshToken);
    }

    @Transactional
    public TokenResponse authenticate(final AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        final User user = repository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        final String accessToken = jwtService.generateToken(user);
        final String refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);
        saveUserToken(user, refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }

    private void saveUserToken(User user, String jwtToken) {
        final Token token = Token.builder()
                .user(user)
                // NOTE: tokens are stored as SHA-256 digests, not raw JWTs. Pre-existing plaintext
                // rows will no longer match any lookup; affected users simply log in again.
                .token(TokenHasher.sha256(jwtToken))
                .tokenType(Token.TokenType.BEARER)
                .expired(false)
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(final User user) {
        final List<Token> validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (!validUserTokens.isEmpty()) {
            validUserTokens.forEach(token -> {
                token.setExpired(true);
                token.setRevoked(true);
            });
            tokenRepository.saveAll(validUserTokens);
        }
    }

    @Transactional
    public TokenResponse refreshToken(@NotNull final String authentication) {
        if (authentication == null || !authentication.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid auth header");
        }
        final String presentedToken = authentication.substring(7);
        if (!jwtService.isRefreshToken(presentedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        final String userEmail;
        try {
            userEmail = jwtService.extractUsername(presentedToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (userEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        final User user = this.repository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Store-backed check: the presented refresh token must exist server-side,
        // unexpired and unrevoked, before anything is issued.
        tokenRepository.findByToken(TokenHasher.sha256(presentedToken))
                .filter(stored -> !stored.isExpired() && !stored.isRevoked())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!jwtService.isTokenValid(presentedToken, user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        // Rotate: revoke the used refresh token together with every other valid
        // token of the user, then issue and persist a fresh pair.
        revokeAllUserTokens(user);

        final String accessToken = jwtService.generateToken(user);
        final String newRefreshToken = jwtService.generateRefreshToken(user);
        saveUserToken(user, accessToken);
        saveUserToken(user, newRefreshToken);

        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(final String email) {
        final User user = repository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        revokeAllUserTokens(user);
    }
}
