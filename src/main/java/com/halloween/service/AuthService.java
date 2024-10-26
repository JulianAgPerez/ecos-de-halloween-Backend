package com.halloween.service;

import com.halloween.controller.auth.LoginRequest;
import com.halloween.controller.auth.RegisterRequest;
import com.halloween.controller.auth.TokenResponse;
import com.halloween.entities.User;
import com.halloween.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse register(RegisterRequest request) {
        var user = User.builder()
                .name(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();
        var savedUser = userRepository.save(user);
        var jwtToken = jwtService.geerateToken(user);
        var refreshToken = jwtService.geerateRefreshToken(user);
        savedUserToken(savedUser, jwtToken);
        return new TokenResponse(jwtToken, refreshToken)
    }

    public TokenResponse login(LoginRequest request) {
    }

    public TokenResponse refreshToken(String authHeader) {
    }
}
