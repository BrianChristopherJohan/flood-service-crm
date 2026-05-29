package com.fyp.floodmonitoring.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixture-based tests that pin the UUIDv5 derivation forever.
 *
 * <p>If any of these break, the implementation has drifted from RFC
 * 4122 §4.3 AND from {@code scripts/seed-from-mongo.mjs} — which means
 * fresh databases will once again diverge from the seeder, breaking
 * cross-service SSO for the admin account. Treat a red test here as
 * a P0.</p>
 */
class UserIdGeneratorTest {

    /**
     * The production admin's UUID. This value is in the live community
     * database (we hand-corrected the CRM database to match this on
     * 2026-05-21 during the post-mortem). The seeder produces the
     * same value from the same inputs. The DataInitializer must too.
     */
    private static final UUID ADMIN_EXPECTED =
            UUID.fromString("99277d17-1731-5d7d-834f-4b64165aa028");

    @Test
    void adminEmailMapsToProductionId() {
        assertEquals(
                ADMIN_EXPECTED,
                UserIdGenerator.forEmail("admin@floodmanagement.com"),
                "If this fails, the seeder and DataInitializer disagree — fresh DBs will drift."
        );
    }

    @Test
    void caseAndWhitespaceAreNormalised() {
        // Same email in different surface forms — must produce the same id.
        UUID a = UserIdGenerator.forEmail("admin@floodmanagement.com");
        UUID b = UserIdGenerator.forEmail("ADMIN@FloodManagement.COM");
        UUID c = UserIdGenerator.forEmail("  admin@floodmanagement.com  ");
        assertEquals(a, b);
        assertEquals(a, c);
    }

    @Test
    void differentEmailsProduceDifferentIds() {
        UUID admin = UserIdGenerator.forEmail("admin@floodmanagement.com");
        UUID other = UserIdGenerator.forEmail("alwin.tayjx.work@gmail.com");
        assertNotEquals(admin, other);
    }

    /**
     * Pin one more fixture — alwin's UUID we observed in production
     * during the cross-DB audit. This catches a class of bug where
     * SHA-1 byte ordering / endianness drifts silently.
     */
    @Test
    void alwinEmailMapsToProductionId() {
        assertEquals(
                UUID.fromString("97a3b956-55b3-5b39-be98-fd41d06a0632"),
                UserIdGenerator.forEmail("alwin.tayjx.work@gmail.com")
        );
    }

    @Test
    void nullEmailIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> UserIdGenerator.forEmail(null));
    }

    @Test
    void uuidVersionAndVariantBitsAreCorrect() {
        UUID id = UserIdGenerator.forEmail("anyone@example.com");
        // Per RFC 4122: version nibble is the high 4 bits of byte 6,
        // which lives in `version()` for the UUID class.
        assertEquals(5, id.version(),
                "UUIDv5 must report version 5 — anything else means " +
                "the bit-mask in UserIdGenerator.v5() is wrong.");
        assertEquals(2, id.variant(),
                "UUIDv5 must report variant 2 (RFC 4122) — anything " +
                "else means the variant-bits mask is wrong.");
    }
}
