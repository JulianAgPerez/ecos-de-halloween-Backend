package com.halloween.config;

import com.halloween.entities.User;
import com.halloween.repository.UserRepository;
import com.halloween.service.JwtService;
import com.halloween.service.TokenHasher;
import com.halloween.repository.TokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

import io.jsonwebtoken.JwtException;
import org.springframework.security.core.Authentication;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            final String userEmail = jwtService.extractUsername(jwt);
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (userEmail != null && authentication == null) {
                final UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                final boolean isStoredTokenValid = tokenRepository.findByToken(TokenHasher.sha256(jwt))
                        .map(token -> !token.isExpired() && !token.isRevoked())
                        .orElse(false);

                if (isStoredTokenValid) {
                    final Optional<User> user = userRepository.findByEmail(userEmail);

                    if (user.isPresent()) {
                        final boolean isTokenValid = jwtService.isTokenValid(jwt, user.get());

                        if (isTokenValid) {
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request)
                            );
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
            }
        } catch (JwtException | UsernameNotFoundException e) {
            // Invalid or expired token: clear any partial context and continue unauthenticated.
            // Protected paths get a clean 401 from the authentication entry point and public
            // paths keep working; no response is written here.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
