package com.halloween.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window rate limiter for the public auth endpoints.
 * Counters live in local memory keyed by client IP and normalized path, so this
 * only protects a single application instance; a multi-instance deployment needs
 * a shared store such as Redis.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String RATE_LIMIT_EXCEEDED_BODY = "{\"error\":\"Too many requests. Please try again later.\"}";
    private static final Set<String> LIMITED_PATHS = Set.of("/auth/login", "/auth/refresh");
    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong(0);
    private final int maxRequestsPerWindow;

    public RateLimitFilter(
            @Value("${application.rate-limit.requests-per-minute:10}") int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(normalizedPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        sweepExpiredWindows(now);
        Window window = windowsByClient.compute(clientKey(request) + "|" + normalizedPath(request), (client, current) -> {
            if (current == null || now - current.startMillis >= WINDOW_MILLIS) {
                return new Window(now);
            }
            current.count++;
            return current;
        });

        if (window.count > maxRequestsPerWindow) {
            long retryAfterSeconds = (window.startMillis + WINDOW_MILLIS - now) / 1000 + 1;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(RATE_LIMIT_EXCEEDED_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // matrix params must not split the rate-limit bucket: /auth/login;x=1 is the
    // same protected path as /auth/login, so both the skip-list and the window key
    // normalize away everything after the first ';'.
    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int semicolon = uri.indexOf(';');
        return semicolon >= 0 ? uri.substring(0, semicolon) : uri;
    }

    // Best-effort, lock-free sweep: the windows map grows one entry per (client,path)
    // pair, so at most once per window drop every expired window to bound memory.
    private void sweepExpiredWindows(long now) {
        long last = lastSweepMillis.get();
        if (now - last >= WINDOW_MILLIS && lastSweepMillis.compareAndSet(last, now)) {
            windowsByClient.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        }
    }

    private String clientKey(HttpServletRequest request) {
        // Render terminates TLS behind a proxy: the original client IP arrives
        // in X-Forwarded-For; fall back to the direct remote address elsewhere.
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long startMillis;
        private int count = 1;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }

        private boolean isExpired(long now) {
            return now - startMillis >= WINDOW_MILLIS;
        }
    }
}
