package com.aidatachat.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthenticationRateLimitFilterTest {

    @Test
    void returns429AndRetryAfterWhenLoginLimitIsExceeded() throws Exception {
        AuthenticationRateLimitFilter filter =
                new AuthenticationRateLimitFilter(
                        2,
                        Duration.ofMinutes(1),
                        10,
                        Duration.ofHours(1),
                        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));
        AtomicInteger accepted = new AtomicInteger();

        assertThat(invoke(filter, accepted).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, accepted).getStatus()).isEqualTo(200);
        MockHttpServletResponse rejected = invoke(filter, accepted);

        assertThat(accepted).hasValue(2);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("rate-limit");
    }

    private MockHttpServletResponse invoke(
            AuthenticationRateLimitFilter filter, AtomicInteger accepted) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request, response, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());
        return response;
    }
}
