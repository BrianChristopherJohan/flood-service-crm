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
 * One-shot startup migrations that widen columns the CRM uploaders write
 * data: URLs to. Hibernate's ddl-auto: update does not alter existing
 * column types, so legacy columns stay at VARCHAR(500) and reject the
 * base64 payloads with "value too long". Each ALTER is idempotent —
 * TEXT → TEXT is a metadata no-op.
 *
 * Runs after ApplicationReadyEvent and @Async so it never blocks boot
 * or the Railway readiness probe.
 */
@Slf4j
@Component
public class AvatarColumnMigration {

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    @Transactional
    public void widenUploaderColumns() {
        widen("users",  "avatar_url");
        widen("blogs",  "image_url");
    }

    private void widen(String table, String column) {
        try {
            em.createNativeQuery(
                "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT"
            ).executeUpdate();
            log.info("[AvatarColumnMigration] {}.{} is TEXT", table, column);
        } catch (Exception e) {
            log.warn("[AvatarColumnMigration] ALTER {}.{} skipped: {}", table, column, e.getMessage());
        }
    }
}
