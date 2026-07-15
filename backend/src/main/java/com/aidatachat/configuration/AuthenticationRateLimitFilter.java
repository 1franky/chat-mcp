package com.aidatachat.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

final class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";

    private final Limit loginLimit;
    private final Limit registrationLimit;
    private final Clock clock;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    AuthenticationRateLimitFilter(
            int loginAttempts,
            Duration loginWindow,
            int registrationAttempts,
            Duration registrationWindow,
            Clock clock) {
        this.loginLimit = new Limit(loginAttempts, loginWindow);
        this.registrationLimit = new Limit(registrationAttempts, registrationWindow);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        Limit limit = limitFor(request.getMethod(), path);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        Decision decision = consume(path + ':' + request.getRemoteAddr(), limit, now);
        if (!decision.allowed()) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            SecurityProblemWriter.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "urn:ai-data-chat:problem:rate-limit",
                    "Demasiadas solicitudes",
                    "Intenta nuevamente mas tarde.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Limit limitFor(String method, String path) {
        if (!"POST".equals(method)) {
            return null;
        }
        return switch (path) {
            case LOGIN_PATH -> loginLimit;
            case REGISTER_PATH -> registrationLimit;
            default -> null;
        };
    }

    private Decision consume(String key, Limit limit, long now) {
        Counter updated =
                counters.compute(
                        key,
                        (ignored, current) -> {
                            if (current == null || now >= current.windowEndsAt()) {
                                return new Counter(1, now + limit.window().toMillis());
                            }
                            return new Counter(current.count() + 1, current.windowEndsAt());
                        });
        boolean allowed = updated.count() <= limit.attempts();
        long retryAfter = Math.max(1, (updated.windowEndsAt() - now + 999) / 1000);
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> now >= entry.getValue().windowEndsAt());
        }
        return new Decision(allowed, retryAfter);
    }

    private record Limit(int attempts, Duration window) {
        private Limit {
            if (attempts < 1 || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Rate limit must be positive");
            }
        }
    }

    private record Counter(int count, long windowEndsAt) {}

    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
