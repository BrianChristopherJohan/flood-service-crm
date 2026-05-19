package com.fyp.floodmonitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QA P0-3 — Startup-time guard that surfaces production-unsafe Spring
 * config in the logs the moment the service comes up.
 *
 * Concretely: warn if {@code hibernate.ddl-auto = update} is active
 * in production. The setting lets JPA mutate the schema based on
 * {@code @Entity} class changes. That's fine in local dev (drop the
 * database, rebuild) but dangerous in prod — an accidental entity
 * rename or removed field silently mutates the live schema with no
 * audit trail.
 *
 * <p>The fix is operational, not code: set {@code HIBERNATE_DDL_AUTO
 * =validate} on Railway and run {@code scripts/apply-migrations.mjs}
 * for every subsequent schema change. This guard makes the misconfig
 * visible instead of letting it sit silently until the day a
 * deployment drops a column.
 *
 * <p>Does NOT prevent startup — the service still boots. We only
 * emit a loud warning so ops + monitoring sees it.
 */
@Slf4j
@Configuration
public class StartupConfigGuard {

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.environment:development}")
    private String environment;

    @Bean
    public ApplicationRunner ddlAutoAuditRunner() {
        return args -> {
            boolean looksProd =
                    "prod".equalsIgnoreCase(activeProfile)
                            || "production".equalsIgnoreCase(activeProfile)
                            || "production".equalsIgnoreCase(environment)
                            || "prod".equalsIgnoreCase(environment);

            boolean mutatesSchema =
                    "update".equalsIgnoreCase(ddlAuto)
                            || "create".equalsIgnoreCase(ddlAuto)
                            || "create-drop".equalsIgnoreCase(ddlAuto);

            if (looksProd && mutatesSchema) {
                log.warn(
                        "[StartupConfigGuard] ⚠ hibernate.ddl-auto={} on profile={} (env={}). "
                                + "Schema can be auto-mutated by JPA. Set HIBERNATE_DDL_AUTO=validate "
                                + "on Railway and apply explicit migrations via scripts/apply-migrations.mjs. "
                                + "See RUNBOOK.md → 'Migrating to Railway Postgres' for the procedure.",
                        ddlAuto, activeProfile, environment);
            } else {
                log.info(
                        "[StartupConfigGuard] hibernate.ddl-auto={} (profile={}, env={}) — OK",
                        ddlAuto, activeProfile, environment);
            }
        };
    }
}
