package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Blog articles shown in the mobile app and managed via the CRM dashboard.
 */
@Entity
@Table(name = "blogs")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Blog {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Bundled image asset key e.g. "blog-1", "blog-2". Used as fallback when imageUrl is absent. */
    @Column(name = "image_key", length = 50)
    private String imageKey;

    /**
     * Hero image. Either a public URL (legacy) or a {@code data:image/...}
     * URL produced by the CRM uploader (new). Stored as TEXT so a resized
     * 1280×720 JPEG (~150-300 KB base64) fits comfortably. Takes precedence
     * over imageKey when set.
     */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Content category e.g. "Flood Alert", "Safety Tips", "Community", "Updates". */
    @Column(name = "category", length = 100)
    private String category;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_featured")
    private Boolean isFeatured;

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
