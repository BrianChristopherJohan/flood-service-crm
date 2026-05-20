package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.enums.Role;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.*;
import com.fyp.floodmonitoring.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Handles all authentication flows:
 * register · login · token refresh · forgot password · verify code · reset password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final List<String> DEFAULT_SETTING_KEYS =
            List.of("pushNotifications", "smsNotifications", "emailNotifications", "lowDataMode");

    private final UserRepository             userRepository;
    private final RefreshTokenRepository     refreshTokenRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final UserSettingRepository      settingRepository;
    private final JwtTokenProvider           tokenProvider;
    private final PasswordEncoder            passwordEncoder;
    private final EmailService               emailService;
    private final com.fyp.floodmonitoring.security.RevokedTokenStore revokedTokenStore;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.environment}")
    private String environment;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponseDto register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw AppException.conflict("An account with this email already exists");
        }

        User user = User.builder()
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.CUSTOMER.getPersistenceValue())
                .build();
        user = userRepository.save(user);

        seedDefaultSettings(user.getId());
        return buildSession(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponseDto login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.unauthorized("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw AppException.unauthorized("Invalid email or password");
        }

        userRepository.updateLastLogin(user.getId(), Instant.now());
        return buildSession(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Server-side revocation of an access token + its paired refresh
     * token. Called by {@code POST /auth/logout}. See community-side
     * AuthService.logout() for the full rationale — same shape, same
     * fail-open semantics on Redis.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String jti = tokenProvider.getJtiFromAccessToken(accessToken);
                long expEpoch = tokenProvider.getExpirySecondsFromAccessToken(accessToken);
                long nowEpoch = System.currentTimeMillis() / 1000L;
                long ttl = expEpoch - nowEpoch;
                if (jti != null && !jti.isBlank() && ttl > 0) {
                    revokedTokenStore.revoke(jti, ttl);
                    log.info("[AuthService.logout] revoked jti={} ttl={}s", jti, ttl);
                }
            } catch (Exception e) {
                log.warn("[AuthService.logout] could not revoke access token: {}", e.getMessage());
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                refreshTokenRepository.deleteByToken(refreshToken);
            } catch (Exception e) {
                log.warn("[AuthService.logout] refresh token cleanup failed: {}", e.getMessage());
            }
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Transactional
    public String refreshAccessToken(String refreshToken) {
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw AppException.unauthorized("Refresh token is invalid or expired");
        }

        UUID userId = tokenProvider.getSubjectFromRefreshToken(refreshToken);

        refreshTokenRepository.findValidToken(refreshToken, userId, Instant.now())
                .orElseThrow(() -> AppException.unauthorized("Refresh token has been revoked"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.unauthorized("User not found"));

        return tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Transactional
    public String forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            // Do not reveal whether the account exists
            return null;
        }

        User user = userOpt.get();

        // Privileged accounts cannot self-reset their password through
        // a public email-based flow. Email account compromise is the
        // most common vector to escalate into an admin takeover, so
        // admins and operations managers must change passwords through
        // the authenticated /auth/change-password endpoint instead.
        // We log + return silently to avoid revealing the role through
        // a different response shape.
        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if ("admin".equals(role) || "operations_manager".equals(role)) {
            log.warn("[Auth] Forgot-password blocked for privileged role={} email={}", role, email);
            return null;
        }

        resetCodeRepository.invalidateAllForUser(user.getId());

        String code = String.format("%06d", new Random().nextInt(900000) + 100000);
        PasswordResetCode resetCode = PasswordResetCode.builder()
                .userId(user.getId())
                .code(code)
                .expiresAt(Instant.now().plusSeconds(600)) // 10 minutes
                .build();
        resetCodeRepository.save(resetCode);

        // Send email asynchronously — does not block the HTTP response
        emailService.sendPasswordResetCode(email, code);
        log.info("[Auth] Password reset code dispatched for {} [env={}]", email, environment);

        // In development mode (no RESEND_API_KEY), the code is also returned in the
        // response so the frontend can auto-fill it for testing without a real inbox.
        // In production this always returns null — the user must check their email.
        return "development".equals(environment) ? code : null;
    }

    // ── Verify reset code ─────────────────────────────────────────────────────

    @Transactional
    public void verifyResetCode(VerifyResetCodeRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("No reset request found for this email"));

        PasswordResetCode record = resetCodeRepository
                .findLatestUnused(user.getId(), req.code())
                .orElseThrow(() -> AppException.badRequest("INVALID_RESET_CODE", "Invalid verification code"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("RESET_CODE_EXPIRED", "The verification code has expired");
        }

        record.setVerified(true);
        resetCodeRepository.save(record);
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("Account not found"));

        // Same-purpose guard as forgotPassword: even if a code was
        // somehow minted before the user got promoted, never let it
        // mutate a privileged-role password through the public flow.
        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if ("admin".equals(role) || "operations_manager".equals(role)) {
            log.warn("[Auth] Reset-password blocked for privileged role={} email={}", role, email);
            throw AppException.forbidden(
                "Privileged accounts must change their password while signed in.");
        }

        PasswordResetCode record = resetCodeRepository
                .findLatestVerifiedUnused(user.getId())
                .orElseThrow(() -> AppException.forbidden("Password reset verification is required"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("RESET_CODE_EXPIRED", "The verification code has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        record.setUsed(true);
        resetCodeRepository.save(record);

        // Revoke all refresh tokens for security
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LoginResponseDto buildSession(User user) {
        String accessToken  = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = tokenProvider.createRefreshToken(user.getId());

        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .build();
        refreshTokenRepository.save(rt);

        Instant accessExpiresAt = Instant.now().plusMillis(accessTokenExpiryMs);
        AuthSessionDto session = new AuthSessionDto(accessToken, refreshToken, accessExpiresAt.toString());
        String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
        UserSummaryDto userDto = new UserSummaryDto(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                displayName,
                user.getAvatarUrl(),
                Role.fromString(user.getRole()).getDisplayLabel());

        return new LoginResponseDto(session, userDto);
    }

    // ── Change password (authenticated) ──────────────────────────────────────

    /**
     * Changes the password for an already-authenticated user.
     * Requires the correct current password — no reset-code flow needed.
     *
     * @param userId  the authenticated user's UUID
     * @param req     contains currentPassword + newPassword
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw AppException.unauthorized("Current password is incorrect");
        }

        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("SAME_PASSWORD", "New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens for security after password change
        refreshTokenRepository.deleteAllByUserId(userId);
        log.info("[Auth] Password changed for userId={}", userId);
    }

    private void seedDefaultSettings(UUID userId) {
        for (String key : DEFAULT_SETTING_KEYS) {
            settingRepository.upsertDefault(userId, key);
        }
    }
}
