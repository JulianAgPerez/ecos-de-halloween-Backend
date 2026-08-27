package com.halloween.config;

import com.halloween.entities.User;
import com.halloween.repository.TokenRepository;
import com.halloween.repository.UserRepository;
import com.halloween.service.JwtService;
import com.halloween.service.TokenHasher;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String REFRESH_ENDPOINT = "/auth/refresh";

    private final JwtService jwtService;
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
                final boolean isStoredTokenValid = tokenRepository.findByToken(TokenHasher.sha256(jwt))
                        .map(token -> !token.isExpired() && !token.isRevoked())
                        .orElse(false);

                if (isStoredTokenValid) {
                    final User user = userRepository.findByEmail(userEmail).orElse(null);

                    if (user != null && jwtService.isTokenValid(jwt, user)) {
                        // A refresh token must only redeem on /auth/refresh; one presented as a
                        // bearer credential anywhere else is rejected outright instead of
                        // granting full access to protected routes.
                        if (jwtService.isRefreshToken(jwt) && !REFRESH_ENDPOINT.equals(request.getRequestURI())) {
                            log.warn("Rejected refresh token used as bearer credential from {}: {}",
                                    request.getRemoteAddr(), request.getRequestURI());
                            writeError(response, HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
                            SecurityContextHolder.clearContext();
                            return;
                        }

                        final org.springframework.security.core.userdetails.UserDetails principal =
                                org.springframework.security.core.userdetails.User.builder()
                                        .username(user.getEmail())
                                        .password(user.getPassword())
                                        .build();
                        final UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (DataAccessException e) {
            // The database is down: fail closed instead of silently continuing unauthenticated.
            log.warn("Token validation aborted, database unavailable: {}", e.getMessage());
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "Database unavailable, try again later");
            SecurityContextHolder.clearContext();
            return;
        } catch (JwtException | UsernameNotFoundException | IllegalArgumentException e) {
            // Invalid or expired token: clear any partial context and continue unauthenticated.
            // Protected paths get a clean 401 from the authentication entry point and public
            // paths keep working; no response is written here. debug (not warn) because public
            // routes attract constant garbage and this would otherwise be a log flood.
            log.debug("Rejected invalid bearer credential from {}: {}",
                    request.getRemoteAddr(), request.getRequestURI());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
