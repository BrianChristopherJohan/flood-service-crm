package com.fyp.floodmonitoring.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Reads {@link RateLimit} off the matched handler method, builds the
 * windows for it, asks {@link RateLimiter} whether the caller has any
 * tokens left, and short-circuits with HTTP 429 if they don't.
 *
 * <p>The interceptor is keyed on the authenticated user id when
 * available, falling back to the client IP otherwise — anonymous abuse
 * still has SOME guard even though unauthenticated endpoints don't
 * usually carry {@link RateLimit}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;
        RateLimit rl = hm.getMethodAnnotation(RateLimit.class);
        if (rl == null) return true;

        List<RateLimiter.Window> windows = RateLimiter.windows(rl.perMinute(), rl.perHour(), rl.perDay());
        if (windows.isEmpty()) return true;

        String userKey = resolveUserKey(req);
        RateLimiter.Decision d = limiter.check(rl.key(), userKey, windows);
        if (d.isAllowed()) return true;

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(d.getRetryAfterSeconds()));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(
                "{\"code\":\"RATE_LIMITED\"," +
                "\"retryAfterSeconds\":" + d.getRetryAfterSeconds() + "," +
                "\"message\":\"You're doing that too quickly. Try again in " + d.getRetryAfterSeconds() + "s.\"}");
        return false;
    }

    private static String resolveUserKey(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return "u:" + auth.getName();
        }
        String xff = req.getHeader("X-Forwarded-For");
        String ip = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        return "ip:" + (ip == null ? "unknown" : ip);
    }
}
