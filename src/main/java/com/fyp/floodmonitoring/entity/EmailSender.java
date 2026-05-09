package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per <strong>email purpose</strong> — picks the from-address
 * a given email category ships from. Lets ops swap senders at runtime
 * (e.g. point password-reset emails at a deliverability-tuned subdomain
 * after a domain reputation incident) without a redeploy.
 *
 * <p>Lookup is keyed by {@code purpose} which corresponds to a stable
 * machine code on the CRM:</p>
 *
 * <pre>
 *   PASSWORD_RESET  -> noreply@floodwatch.app   (admin reset codes)
 *   BROADCAST       -> alerts@floodwatch.app    (mass alert blasts)
 * </pre>
 *
 * Fallback chain when a row is missing or inactive: the legacy
 * {@code app.email.from-address} env var, then a hardcoded
 * {@code noreply@floodwatch.app}. The resolver caches each lookup
 * so the table is hit at most once per purpose per JVM.
 */
@Entity
@Table(name = "email_senders")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class EmailSender {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Stable machine code for the purpose this sender serves. Unique. */
    @Column(nullable = false, length = 32, unique = true)
    private String purpose;

    /** Email address the message ships from. */
    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    /** Optional friendly name shown in the inbox (e.g. "FloodWatch Alerts"). */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Inactive rows are ignored by the resolver and fall through to the env-var default. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
