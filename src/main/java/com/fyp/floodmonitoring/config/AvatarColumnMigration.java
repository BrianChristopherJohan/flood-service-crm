package com.fyp.floodmonitoring.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * One-shot startup migrations that widen columns the CRM uploaders write
 * data: URLs to. Hibernate's {@code ddl-auto: update} does not alter
 * existing column types, so legacy columns stay at {@code VARCHAR(500)}
 * and reject the base64 payloads with "value too long" — which the
 * global exception handler renders as a generic 500.
 *
 * <p>Each ALTER runs on its own JDBC connection in auto-commit mode, so
 * a failure on one (e.g. table missing, permission denied) cannot poison
 * the next. The DDL itself is idempotent — {@code TEXT → TEXT} is a
 * metadata no-op on Postgres.</p>
 *
 * <p>Runs after {@link ApplicationReadyEvent} and {@code @Async} so it
 * never blocks boot or the Railway readiness probe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarColumnMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void widenUploaderColumns() {
        widen("users",  "avatar_url");
        widen("blogs",  "image_url");
    }

    private void widen(String table, String column) {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            // Auto-commit by default on a fresh connection — each ALTER is
            // its own transaction so one failure doesn't poison the next.
            s.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT");
            log.info("[AvatarColumnMigration] {}.{} is TEXT", table, column);
        } catch (Exception e) {
            log.warn("[AvatarColumnMigration] ALTER {}.{} skipped: {}", table, column, e.getMessage());
        }
    }
}
