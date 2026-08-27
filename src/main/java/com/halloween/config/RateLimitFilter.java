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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
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
    private final List<Subnet> trustedProxies;

    public RateLimitFilter(
            @Value("${application.rate-limit.requests-per-minute:10}") int maxRequestsPerWindow,
            @Value("${application.rate-limit.trusted-proxies:}") String trustedProxies) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.trustedProxies = parseTrustedProxies(trustedProxies);
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
        // Never trust X-Forwarded-For verbatim: an attacker can rotate the header on
        // every request to dodge the limiter. The header is only honored when the
        // DIRECT peer (request.getRemoteAddr()) is a proxy we configured as trusted;
        // otherwise we key on the remote address alone and ignore XFF entirely.
        final String remote = request.getRemoteAddr();
        final String xff = firstForwardedFor(request);
        if (xff != null && isTrustedProxy(remote)) {
            return xff;
        }
        return remote;
    }

    private static String firstForwardedFor(HttpServletRequest request) {
        final String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        return forwardedFor.split(",")[0].trim();
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        for (Subnet subnet : trustedProxies) {
            if (subnet.contains(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    // Minimal IPv4 CIDR matcher (no external dependency). An entry must parse as
    // "address/prefix"; any malformed entry is ignored.
    private static List<Subnet> parseTrustedProxies(String raw) {
        final List<Subnet> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String entry : raw.split(",")) {
            final String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final Subnet subnet = Subnet.parse(trimmed);
            if (subnet != null) {
                result.add(subnet);
            }
        }
        return result;
    }

    private static final class Subnet {
        private final int base;
        private final int mask;

        private Subnet(int base, int mask) {
            this.base = base;
            this.mask = mask;
        }

        private static Subnet parse(String cidr) {
            final int slash = cidr.indexOf('/');
            if (slash < 0) {
                return null;
            }
            final String addressPart = cidr.substring(0, slash);
            final String prefixPart = cidr.substring(slash + 1);
            try {
                final int prefix = Integer.parseInt(prefixPart.trim());
                if (prefix < 0 || prefix > 32) {
                    return null;
                }
                final byte[] bytes = InetAddress.getByName(addressPart.trim()).getAddress();
                if (bytes.length != 4) {
                    return null;
                }
                final int address = ((bytes[0] & 0xFF) << 24)
                        | ((bytes[1] & 0xFF) << 16)
                        | ((bytes[2] & 0xFF) << 8)
                        | (bytes[3] & 0xFF);
                final int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
                return new Subnet(address, mask);
            } catch (Exception e) {
                return null;
            }
        }

        private boolean contains(String ip) {
            try {
                final byte[] bytes = InetAddress.getByName(ip).getAddress();
                if (bytes.length != 4) {
                    return false;
                }
                final int address = ((bytes[0] & 0xFF) << 24)
                        | ((bytes[1] & 0xFF) << 16)
                        | ((bytes[2] & 0xFF) << 8)
                        | (bytes[3] & 0xFF);
                return (address & mask) == (base & mask);
            } catch (Exception e) {
                return false;
            }
        }
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
