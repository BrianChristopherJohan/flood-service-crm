package com.fyp.floodmonitoring.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Deterministic UUIDv5 generation for shared identities (the admin user
 * and any other account that exists in BOTH the community and CRM
 * databases).
 *
 * <p><b>Why this exists:</b> the two Java services have their own
 * Postgres databases. The {@code users} table is duplicated — the same
 * email lives in both DBs as two separate rows. For SSO to work
 * across the boundary, the {@code id} (which becomes the JWT
 * {@code sub} claim) MUST match in both DBs. If community Java signs
 * a token with {@code sub=abc123}, and CRM Java looks up
 * {@code sub=abc123} in its DB and finds nothing, every cross-service
 * call (CRM-Vercel → CRM-Java /profile during SSO callback) 401s.</p>
 *
 * <p>Previously {@link DataInitializer} let JPA's {@code @UuidGenerator}
 * mint a random UUID per database — drift was guaranteed. This helper
 * fixes both DataInitializers AND the {@code seed-from-mongo.mjs}
 * runner to use the same deterministic UUIDv5(NS_USER, email)
 * derivation. Same input → same output, regardless of which side ran
 * first.</p>
 *
 * <p><b>Namespace MUST match the seeder:</b>
 * {@code 8a9d6f1c-7c5b-4f3e-9c1b-6a3d4e5f6a7c} — defined as
 * {@code NS_USER} in {@code scripts/seed-from-mongo.mjs} line ~111.
 * Changing this string here without also changing it in the seeder
 * (and vice-versa) re-introduces the drift bug. Don't.</p>
 */
public final class UserIdGenerator {

    /** Per RFC 4122 §4.3 — same UUIDv5 namespace as scripts/seed-from-mongo.mjs. */
    private static final UUID NS_USER =
            UUID.fromString("8a9d6f1c-7c5b-4f3e-9c1b-6a3d4e5f6a7c");

    private UserIdGenerator() {}

    /**
     * UUIDv5(NS_USER, lowercase trimmed email). Stable across processes,
     * deployments, and databases. Identical to what
     * {@code uuidv5(email.toLowerCase().trim(), NS_USER)} produces in
     * the Node seeder.
     *
     * @param email account email — case-insensitive, whitespace tolerated
     * @return deterministic UUID for the same email everywhere
     */
    public static UUID forEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email cannot be null");
        }
        return v5(NS_USER, email.toLowerCase().trim());
    }

    /**
     * Reference UUIDv5 implementation per RFC 4122 §4.3:
     *   1. SHA-1 hash of (namespace bytes || name UTF-8 bytes)
     *   2. Take first 16 bytes
     *   3. Set version nibble to 5 (high 4 bits of byte 6)
     *   4. Set variant bits to RFC 4122 (high 2 bits of byte 8 = 10)
     *
     * <p>Sanity-check fixture (kept in unit tests, not here):
     * v5({@link #NS_USER}, "admin@floodmanagement.com") must equal
     * {@code 99277d17-1731-5d7d-834f-4b64165aa028}. This is the
     * production admin's UUID — if the implementation drifts, admin
     * SSO across services breaks silently.</p>
     */
    static UUID v5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(toBytes(namespace));
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            // Version 5: bits [4..7] of byte 6 → 0101
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            // Variant RFC 4122: bits [6..7] of byte 8 → 10
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            long msb = 0L;
            long lsb = 0L;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xffL);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xffL);
            }
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is mandatory in every JRE — this is structurally
            // unreachable. Wrap so callers don't need a checked catch.
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /** Encode a UUID as the 16 big-endian bytes RFC 4122 specifies. */
    private static byte[] toBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (msb >>> (56 - i * 8));
        }
        for (int i = 0; i < 8; i++) {
            out[i + 8] = (byte) (lsb >>> (56 - i * 8));
        }
        return out;
    }
}
