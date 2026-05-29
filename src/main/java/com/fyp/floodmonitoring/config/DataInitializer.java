package com.fyp.floodmonitoring.config;

import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Boots the default admin account on every startup and re-asserts its
 * role so it can never get locked out by an accidental "role change"
 * via the admin-user UI.
 *
 * <p>The default admin email is hard-coded as
 * {@link com.fyp.floodmonitoring.config.AdminInvariants#DEFAULT_ADMIN_EMAIL}
 * and protected at the service layer (see {@code AdminUserService}) so
 * it cannot be deleted or have its role changed via API. Duplicate
 * users with the same email are blocked by the {@code users.email}
 * UNIQUE constraint.</p>
 *
 * <p>If the account doesn't exist yet it's created using the
 * {@code ADMIN_SEED_PASSWORD} env var. We deliberately don't ship a
 * hard-coded default password — when the var is unset and the row is
 * missing, we log a loud warning and skip creation rather than seed an
 * insecure password into the database. Operator action required.</p>
 *
 * <p>If a separate {@code ADMIN_SEED_EMAIL} is configured (legacy /
 * staging accounts), it is also seeded / re-promoted, on top of the
 * default admin.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-email:}")
    private String seedEmail;

    @Value("${app.admin.seed-password:}")
    private String seedPassword;

    @Value("${app.admin.seed-first-name:Admin}")
    private String seedFirstName;

    @Value("${app.admin.seed-last-name:User}")
    private String seedLastName;

    @Override
    public void run(String... args) {
        enforceDefaultAdmin();
        seedExtraAdminFromEnv();
    }

    /**
     * Always present + always role=admin. Runs on every startup, so
     * even if someone changes the role via API or directly in the DB,
     * the next deploy/restart resets it.
     */
    private void enforceDefaultAdmin() {
        final String email = AdminInvariants.DEFAULT_ADMIN_EMAIL;
        userRepository.findByEmail(email).ifPresentOrElse(
            existing -> {
                boolean changed = false;
                if (!"admin".equals(existing.getRole())) {
                    log.warn("[DataInitializer] Default admin {} had role '{}' — resetting to 'admin'",
                            email, existing.getRole());
                    existing.setRole("admin");
                    changed = true;
                }
                if (changed) userRepository.save(existing);
            },
            () -> {
                if (seedPassword == null || seedPassword.isBlank()) {
                    log.warn("[DataInitializer] Default admin {} does not exist AND " +
                             "ADMIN_SEED_PASSWORD is unset — set the env var to provision " +
                             "the default admin. Skipping for now.", email);
                    return;
                }
                User admin = User.builder()
                        // Deterministic UUIDv5(email) so this CRM admin
                        // shares the SAME id as the community admin
                        // with the same email — critical for cross-
                        // service SSO. See UserIdGenerator for the
                        // post-mortem on the drift bug this prevents.
                        .id(UserIdGenerator.forEmail(email))
                        .firstName(seedFirstName.trim())
                        .lastName(seedLastName.trim())
                        .email(email)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .role("admin")
                        .build();
                userRepository.save(admin);
                log.info("[DataInitializer] Created default admin {} with id={}",
                        email, admin.getId());
            }
        );
    }

    /** Optional second admin from ADMIN_SEED_EMAIL — useful for staging. */
    private void seedExtraAdminFromEnv() {
        if (seedEmail.isBlank() || seedPassword.isBlank()) return;

        final String email = seedEmail.toLowerCase().trim();
        if (email.equalsIgnoreCase(AdminInvariants.DEFAULT_ADMIN_EMAIL)) {
            return; // already handled by enforceDefaultAdmin
        }
        userRepository.findByEmail(email).ifPresentOrElse(
            existing -> {
                if (!"admin".equals(existing.getRole())) {
                    existing.setRole("admin");
                    userRepository.save(existing);
                    log.info("[DataInitializer] Promoted {} to admin", email);
                }
            },
            () -> {
                User admin = User.builder()
                        // Same UUIDv5(email) derivation as the default
                        // admin above — every admin row in this DB has
                        // a deterministic id derived from its email,
                        // matching what the community service + the
                        // seeder produce for the same input.
                        .id(UserIdGenerator.forEmail(email))
                        .firstName(seedFirstName.trim())
                        .lastName(seedLastName.trim())
                        .email(email)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .role("admin")
                        .build();
                userRepository.save(admin);
                log.info("[DataInitializer] Created extra admin {} with id={}",
                        email, admin.getId());
            }
        );
    }
}
