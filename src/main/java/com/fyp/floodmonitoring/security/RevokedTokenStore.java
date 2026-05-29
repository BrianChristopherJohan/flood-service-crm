package com.fyp.floodmonitoring.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-token revocation set, backed by Redis when configured and an
 * in-process {@link ConcurrentHashMap} otherwise.
 *
 * <p>The pattern mirrors the {@code RateLimiter} (fail-open semantics:
 * a Redis outage never causes an availability outage; revocation just
 * becomes best-effort until the connection recovers). The store keys
 * by the JWT's {@code jti} claim and uses a TTL equal to the token's
 * remaining lifetime — no point keeping the entry past natural expiry,
 * the signature check would have rejected the token anyway.</p>
 *
 * <p>Wired by {@link JwtAuthenticationFilter#doFilterInternal}: every
 * incoming access token is checked here BEFORE Spring's authentication
 * context is populated. A revoked token is treated identically to an
 * expired one — the filter ignores it, the request continues
 * unauthenticated, and any endpoint that requires authentication
 * returns 401.</p>
 *
 * <p>Key shape: {@code revoked_jti:<jti>}. We keep that namespace
 * stable across both Java services so a single Redis instance can
 * serve both backends if they ever consolidate. Today they run on
 * separate Redis dbs but the key prefix is service-agnostic.</p>
 */
@Slf4j
@Component
public class RevokedTokenStore {

    private static final String KEY_PREFIX = "revoked_jti:";

    /** Sentinel value stored against each revoked jti. The value is
     *  unused — presence of the key is the signal. */
    private static final String SENTINEL = "1";

    private final StringRedisTemplate redis; // nullable when REDIS_URL is empty
    private final ConcurrentHashMap<String, Long> fallback = new ConcurrentHashMap<>();

    public RevokedTokenStore(
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this.redis = provider.getIfAvailable();
    }

    /**
     * Mark a jti as revoked for the given TTL.
     *
     * @param jti        the JWT ID claim from the access token
     * @param ttlSeconds how long to remember the revocation; should be
     *                   {@code exp - now}, i.e. the token's remaining
     *                   lifetime. Values ≤ 0 are clamped to 60 seconds
     *                   (defensive — a "just-expired" token shouldn't
     *                   leak into the system if the clocks drift).
     */
    public void revoke(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            log.debug("[RevokedTokenStore] revoke called with blank jti — ignoring");
            return;
        }
        long ttl = Math.max(60L, ttlSeconds);
        String key = KEY_PREFIX + jti;
        if (redis != null) {
            try {
                redis.opsForValue().set(key, SENTINEL, Duration.ofSeconds(ttl));
                return;
            } catch (Exception e) {
                // Fail open — never let Redis outage block the logout
                // flow. Fall through to the in-memory fallback so the
                // process's own JVM still rejects the revoked token
                // until restart.
                log.warn("[RevokedTokenStore] redis revoke failed for {}: {}", key, e.getMessage());
            }
        }
        fallback.put(jti, System.currentTimeMillis() / 1000L + ttl);
    }

    /**
     * Has this jti been revoked? Used by the JWT filter.
     *
     * <p>Returns {@code false} when:</p>
     * <ul>
     *   <li>jti is missing/blank (pre-revocation tokens minted before
     *       this feature shipped — they're grandfathered in)</li>
     *   <li>Redis is configured but throws (fail-open)</li>
     *   <li>The jti isn't in the set</li>
     * </ul>
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        String key = KEY_PREFIX + jti;
        if (redis != null) {
            try {
                Boolean has = redis.hasKey(key);
                return Boolean.TRUE.equals(has);
            } catch (Exception e) {
                log.warn("[RevokedTokenStore] redis check failed for {}: {}", key, e.getMessage());
                // Fall through to the in-memory fallback so a brief
                // Redis blip doesn't accidentally re-authenticate
                // a token we revoked in-process moments ago.
            }
        }
        Long expiresAt = fallback.get(jti);
        if (expiresAt == null) return false;
        if (expiresAt < System.currentTimeMillis() / 1000L) {
            fallback.remove(jti);
            return false;
        }
        return true;
    }
}
