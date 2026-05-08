package com.fyp.floodmonitoring.config;

/**
 * Immutable invariants that protect the default admin account from
 * being deleted, demoted, or duplicated. Referenced by
 * {@link DataInitializer} (re-asserted at every startup) and
 * {@code AdminUserService} (enforced on every PATCH / DELETE).
 *
 * The email is hard-coded on purpose — a senior PM asked for a
 * "default admin that can never be removed" so the FYP demo is
 * always recoverable. The password is NOT hard-coded; it must be
 * supplied via the {@code ADMIN_SEED_PASSWORD} env var on first
 * boot. Once provisioned, password rotation goes through the
 * normal change-password flow.
 */
public final class AdminInvariants {

    /** The protected default-admin email. Cannot be deleted, role-changed, or re-used. */
    public static final String DEFAULT_ADMIN_EMAIL = "admin@floodmanagement.com";

    private AdminInvariants() {}

    public static boolean isProtectedDefaultAdmin(String email) {
        return email != null && DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }
}
