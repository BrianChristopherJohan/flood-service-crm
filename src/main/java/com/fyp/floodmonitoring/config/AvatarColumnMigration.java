package com.fyp.floodmonitoring.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot startup migration that widens {@code users.avatar_url} from
 * {@code VARCHAR(500)} to {@code TEXT}. Mirrors the community-service
 * variant — both write the same shared users table.
 *
 * Runs after the readiness probe so it can't block boot; idempotent.
 */
@Slf4j
@Component
public class AvatarColumnMigration {

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    @Transactional
    public void widenAvatarUrlColumn() {
        try {
            em.createNativeQuery("ALTER TABLE users ALTER COLUMN avatar_url TYPE TEXT")
              .executeUpdate();
            log.info("[AvatarColumnMigration] users.avatar_url is TEXT");
        } catch (Exception e) {
            log.warn("[AvatarColumnMigration] ALTER skipped: {}", e.getMessage());
        }
    }
}
