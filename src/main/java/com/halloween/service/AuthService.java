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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
            mitigateTimingBasedUserEnumeration(request.email());
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

    private static final String DUMMY_PASSWORD = "timing-equalizer";

    // Unknown users skip the bcrypt comparison inside DaoAuthenticationProvider; burn the
    // same encoding cost so login timing does not reveal whether an email is registered.
    private void mitigateTimingBasedUserEnumeration(final String email) {
        if (repository.findByEmail(email).isEmpty()) {
            final String dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
            passwordEncoder.matches(DUMMY_PASSWORD, dummyHash);
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

        // Atomic gate: the conditional UPDATE is the single authority on whether the
        // presented token may still be redeemed; a concurrent second redemption hits
        // revoked=true and returns 0 rows, so it cannot double-issue a fresh pair.
        if (tokenRepository.revokeTokenIfValid(TokenHasher.sha256(presentedToken)) == 0) {
            log.warn("Refresh redemption rejected for: {}", userEmail == null ? "<unknown>" : userEmail);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

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
