package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.EmailSender;
import com.fyp.floodmonitoring.repository.EmailSenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the from-address (and optional display name) for a given
 * email purpose. Lookups are cached per JVM so the table is read at
 * most once per purpose per process lifetime; ops can clear the cache
 * via {@link #invalidate()} after editing rows in the admin UI.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Active row in {@code email_senders} matching the purpose.</li>
 *   <li>The legacy {@code app.email.from-address} env var (back-compat
 *       with deployments that haven't seeded the table yet).</li>
 *   <li>Hardcoded {@code noreply@floodwatch.app}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderResolver {

    /** Stable purpose codes. Add new ones here AND seed a row in the migration. */
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String BROADCAST      = "BROADCAST";

    private static final String HARDCODED_FALLBACK = "noreply@floodwatch.app";

    private final EmailSenderRepository repo;

    @Value("${app.email.from-address:}")
    private String legacyEnvFallback;

    private final ConcurrentHashMap<String, ResolvedSender> cache = new ConcurrentHashMap<>();

    /** Returns the from-address to use for the given purpose. Never null. */
    public String addressFor(String purpose) {
        return resolve(purpose).address();
    }

    /**
     * Returns the address formatted with the optional display name —
     * e.g. {@code "FloodWatch Alerts <alerts@floodwatch.app>"}. Most
     * SMTP clients accept this RFC 5322 format. Falls back to a bare
     * address when no display name is configured.
     */
    public String headerFor(String purpose) {
        ResolvedSender s = resolve(purpose);
        if (s.displayName() == null || s.displayName().isBlank()) return s.address();
        return s.displayName() + " <" + s.address() + ">";
    }

    public ResolvedSender resolve(String purpose) {
        return cache.computeIfAbsent(purpose, this::loadOnce);
    }

    /** Drop every cached entry. Call after rows in {@code email_senders} change. */
    public void invalidate() {
        cache.clear();
    }

    private ResolvedSender loadOnce(String purpose) {
        return repo.findByPurposeIgnoreCaseAndActiveTrue(purpose)
                .map(EmailSenderResolver::fromEntity)
                .orElseGet(() -> {
                    String addr = (legacyEnvFallback != null && !legacyEnvFallback.isBlank())
                            ? legacyEnvFallback
                            : HARDCODED_FALLBACK;
                    log.info("[EmailSender] No active row for purpose={} — falling back to {}", purpose, addr);
                    return new ResolvedSender(addr, null);
                });
    }

    private static ResolvedSender fromEntity(EmailSender s) {
        return new ResolvedSender(s.getFromAddress(), s.getDisplayName());
    }

    public record ResolvedSender(String address, String displayName) {}
}
