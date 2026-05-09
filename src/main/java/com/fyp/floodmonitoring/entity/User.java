package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code users} table.
 * Merged from MongoDB user_admins + user_customers collections.
 */
@Entity
@Table(name = "users")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Lowercase persistence value: {@code admin}, {@code operations_manager}, … */
    @Column(nullable = false, length = 32)
    private String role;

    @Column(length = 50)
    private String phone;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    /**
     * Profile picture. Either a public URL (legacy) or a {@code data:image/...}
     * URL produced by the in-app uploader (new). Stored as TEXT so a small
     * base64-encoded JPEG (~50KB after client-side resize) fits comfortably.
     */
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "last_login")
    private Instant lastLogin;

    /** Expo push token registered by the mobile app — nullable until the user grants permission. */
    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
