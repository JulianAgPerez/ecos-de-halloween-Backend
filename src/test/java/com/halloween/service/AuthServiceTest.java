package com.halloween.service;

import com.halloween.controller.auth.RegisterRequest;
import com.halloween.controller.auth.TokenResponse;
import com.halloween.entities.User;
import com.halloween.repository.Token;
import com.halloween.repository.TokenRepository;
import com.halloween.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository repository;
    @Mock private TokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repository, tokenRepository, passwordEncoder, jwtService, authenticationManager);
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
    void register_withDuplicateEmail_throwsIllegalArgumentException() {
        when(repository.existsByEmail("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Admin", "pass", "admin@test.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void register_savesUserWithEncodedPasswordAndReturnsTokens() {
        when(repository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("plainpass")).thenReturn("encoded");
        when(repository.save(any(User.class))).thenReturn(user());
        when(jwtService.generateToken(user())).thenReturn("access");
        when(jwtService.generateRefreshToken(user())).thenReturn("refresh");

        TokenResponse response = authService.register(new RegisterRequest("Admin", "plainpass", "admin@test.com"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(repository).save(any(User.class));
        verify(tokenRepository).save(any(Token.class));
    }

    @Test
    void authenticate_withValidCredentials_returnsTokensAndRevokesOldOnes() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(repository.findByEmail("admin@test.com")).thenReturn(Optional.of(user()));
        when(jwtService.generateToken(user())).thenReturn("access");
        when(jwtService.generateRefreshToken(user())).thenReturn("refresh");
        when(tokenRepository.findAllValidTokenByUser(1L)).thenReturn(List.of(Token.builder().build()));

        TokenResponse response = authService.authenticate(new AuthRequest("admin@test.com", "plainpass"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(tokenRepository).saveAll(anyList());
    }

    @Test
    void authenticate_withBadCredentials_throwsUnauthorized() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.authenticate(new AuthRequest("admin@test.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshToken_withValidRefreshToken_returnsNewAccessToken() {
        when(jwtService.extractUsername("refresh")).thenReturn("admin@test.com");
        when(repository.findByEmail("admin@test.com")).thenReturn(Optional.of(user()));
        when(jwtService.isTokenValid("refresh", user())).thenReturn(true);
        when(jwtService.generateToken(user())).thenReturn("new-access");

        TokenResponse response = authService.refreshToken("Bearer refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(tokenRepository).save(any(Token.class));
    }

    @Test
    void refreshToken_withInvalidHeader_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> authService.refreshToken("not-bearer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshToken_withInvalidRefreshToken_throwsUnauthorized() {
        when(jwtService.extractUsername("refresh")).thenReturn("admin@test.com");
        when(repository.findByEmail("admin@test.com")).thenReturn(Optional.of(user()));
        when(jwtService.isTokenValid("refresh", user())).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("Bearer refresh"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
