package com.halloween.service;

import com.halloween.entities.User;
import com.halloween.controller.auth.LoginRequest;
import com.halloween.controller.auth.RegisterRequest;
import com.halloween.controller.auth.TokenResponse;
import com.halloween.repository.Token;
import com.halloween.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        final String jwtToken = jwtService.geerateToken(savedUser);
        final String refreshToken = jwtService.geerateRefreshToken(savedUser);

        saveUserToken(savedUser, jwtToken);
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
            throw new IllegalArgumentException("Invalid email or password");
        }

        final User user = repository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        final String accessToken = jwtService.geerateToken(user);
        final String refreshToken = jwtService.geerateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);
        return new TokenResponse(accessToken, refreshToken);
    }


    private void saveUserToken(User user, String jwtToken) {
        final Token token = Token.builder()
                .user(user)
                .token(jwtToken)
                .tokenType(Token.TokenType.BEARER)
                .expired(false)
                .revoked(false)
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

    public TokenResponse refreshToken(@NotNull final String authentication) {
        if (authentication == null || !authentication.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid auth header");
        }
        final String refreshToken = authentication.substring(7);
        final String userEmail = jwtService.extractUsername(refreshToken);
        if (userEmail == null) {
            throw new IllegalArgumentException("Invalid token");
        }

        final User user = this.repository.findByEmail(userEmail).orElseThrow(() -> new IllegalArgumentException("User not found"));
        final boolean isTokenValid = jwtService.isTokenValid(refreshToken, user);
        if (!isTokenValid) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        final String accessToken = jwtService.geerateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse login(LoginRequest request) {
        AuthRequest authRequest = new AuthRequest(request.email(), request.password());
        return authenticate(authRequest);
    }
}
