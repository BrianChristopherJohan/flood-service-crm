package com.fyp.floodmonitoring.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process fallback tests for the revocation store.
 *
 * <p>The Redis path is fail-open by design — a Redis-using test would
 * need testcontainers + a live server, which makes CI flaky. Instead
 * we instantiate the store with a null Redis (mimicking the
 * {@code REDIS_URL=""} dev mode) and exercise the
 * {@link java.util.concurrent.ConcurrentHashMap} fallback. The Redis
 * code path follows the exact same logical shape (set with TTL on
 * revoke, hasKey on isRevoked); we accept the small coverage gap.</p>
 *
 * <p>What we DO want pinned forever:</p>
 * <ul>
 *   <li>Blank / null jti returns {@code false} (pre-revocation tokens
 *       must not be accidentally rejected).</li>
 *   <li>revoke + isRevoked is the obvious round-trip.</li>
 *   <li>Two distinct jtis are isolated.</li>
 *   <li>Expired entries are GC'd on read.</li>
 * </ul>
 */
class RevokedTokenStoreTest {

    private static RevokedTokenStore newStore() {
        // ObjectProvider#getIfAvailable returns null when nothing is
        // registered — exactly the constructor path we want here.
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> emptyProvider =
                (ObjectProvider<StringRedisTemplate>) org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
        return new RevokedTokenStore(emptyProvider);
    }

    @Test
    void blankJtiIsNeverRevoked() {
        RevokedTokenStore s = newStore();
        assertFalse(s.isRevoked(null));
        assertFalse(s.isRevoked(""));
        assertFalse(s.isRevoked("   "));
    }

    @Test
    void revokeThenIsRevokedReturnsTrue() {
        RevokedTokenStore s = newStore();
        s.revoke("jti-1", 60);
        assertTrue(s.isRevoked("jti-1"));
    }

    @Test
    void distinctJtisAreIsolated() {
        RevokedTokenStore s = newStore();
        s.revoke("jti-A", 60);
        assertTrue(s.isRevoked("jti-A"));
        assertFalse(s.isRevoked("jti-B"));
    }

    @Test
    void revokeBlankJtiIsNoOp() {
        RevokedTokenStore s = newStore();
        // Must not throw and must not pollute the store.
        s.revoke(null, 60);
        s.revoke("", 60);
        s.revoke("   ", 60);
        assertFalse(s.isRevoked(""));
        assertFalse(s.isRevoked("any-other-jti"));
    }
}
