package com.fyp.floodmonitoring.security.ratelimit;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window per-user counter, backed by Redis when configured and an
 * in-process {@link ConcurrentHashMap} otherwise (local dev or when
 * Redis is unreachable). Fail-open semantics — the limiter never blocks
 * a request because of an infrastructure error; an outage on Redis must
 * not also produce an availability outage.
 *
 * Algorithm: for each window length W,
 *   bucket = floor(epochSeconds / W) * W
 *   key    = "rl:{name}:{user}:{W}:{bucket}"
 *   n      = INCR(key);  if n == 1 EXPIRE(key, W)
 *   block if n > limit, retry-after = (bucket + W) - now
 */
@Slf4j
@Component
public class RateLimiter {

    private final StringRedisTemplate redis; // nullable when REDIS_URL is empty
    private final ConcurrentHashMap<String, FallbackBucket> fallback = new ConcurrentHashMap<>();

    public RateLimiter(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this.redis = provider.getIfAvailable();
    }

    /**
     * @param bucketName logical bucket (e.g. {@code "community.posts.create"})
     * @param userKey    stable identifier for the caller (user UUID, fallback to IP)
     * @param windows    list of (windowSeconds, limit) pairs to enforce in order
     * @return Decision describing allow vs deny and the retry-after window
     */
    public Decision check(String bucketName, String userKey, List<Window> windows) {
        long now = System.currentTimeMillis() / 1000L;
        for (Window w : windows) {
            long bucket = (now / w.seconds) * w.seconds;
            String key = "rl:" + bucketName + ":" + userKey + ":" + w.seconds + ":" + bucket;
            long count = incr(key, w.seconds);
            if (count > w.limit) {
                long retryAfter = (bucket + w.seconds) - now;
                log.info("[RateLimit] DENY bucket={} user={} window={}s count={} limit={} retryAfter={}",
                        bucketName, userKey, w.seconds, count, w.limit, retryAfter);
                return new Decision(false, Math.max(1, retryAfter));
            }
        }
        return new Decision(true, 0);
    }

    private long incr(String key, long ttlSeconds) {
        if (redis != null) {
            try {
                Long v = redis.opsForValue().increment(key);
                if (v != null && v == 1L) {
                    redis.expire(key, Duration.ofSeconds(ttlSeconds));
                }
                return v == null ? 0 : v;
            } catch (Exception e) {
                // Fail open — never let Redis outage block requests.
                log.warn("[RateLimit] redis incr failed for {}: {}", key, e.getMessage());
            }
        }
        // In-memory fallback. Same fixed-window semantics, single-process scope.
        FallbackBucket b = fallback.computeIfAbsent(key, k -> new FallbackBucket(ttlSeconds));
        return b.incrementAndCleanup();
    }

    /** A single window the limiter must enforce. */
    @Value
    public static class Window {
        long seconds;
        int  limit;
    }

    @Value
    public static class Decision {
        boolean allowed;
        long retryAfterSeconds;
    }

    /** Local in-memory counter used when Redis is unavailable. */
    private static final class FallbackBucket {
        private final long ttlSeconds;
        private final long createdAt;
        private long count;

        FallbackBucket(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            this.createdAt  = System.currentTimeMillis() / 1000L;
        }

        synchronized long incrementAndCleanup() {
            long now = System.currentTimeMillis() / 1000L;
            if (now - createdAt >= ttlSeconds) {
                // Window expired — reset.
                count = 0;
            }
            return ++count;
        }
    }

    /** Convenience builder so callers can describe windows inline. */
    public static List<Window> windows(int perMinute, int perHour, int perDay) {
        List<Window> out = new ArrayList<>(3);
        if (perMinute > 0) out.add(new Window(60,    perMinute));
        if (perHour   > 0) out.add(new Window(3_600, perHour));
        if (perDay    > 0) out.add(new Window(86_400, perDay));
        return out;
    }
}
