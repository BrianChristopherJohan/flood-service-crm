package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.security.ratelimit.RateLimit;
import com.fyp.floodmonitoring.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Public authentication endpoints (no JWT required).
 *
 * <pre>
 * POST /auth/register
 * POST /auth/login
 * POST /auth/refresh
 * POST /auth/forgot-password
 * POST /auth/verify-reset-code
 * POST /auth/reset-password
 * POST /auth/change-password  (authenticated — changes password using current password)
 * </pre>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Rate limit rationale (per QA Sprint-1 P0-5) — see community-side
    // AuthController for the long-form explanation. Same numbers across
    // both services so an attacker can't sidestep one limit by hitting
    // the other backend. Per-bucket counters are isolated per service
    // because they're stored on separate Redis instances (the two
    // services don't share Redis state today; SSO codes are the only
    // cross-service Redis traffic and live on a different namespace).

    @PostMapping("/register")
    @RateLimit(key = "auth.register", perMinute = 3, perHour = 5, perDay = 30)
    public ResponseEntity<LoginResponseDto> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    @RateLimit(key = "auth.login", perMinute = 5, perHour = 10)
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @RateLimit(key = "auth.refresh", perMinute = 30, perHour = 200)
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REFRESH_TOKEN", "message", "Refresh token is missing or invalid"));
        }
        String accessToken = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

    @PostMapping("/forgot-password")
    @RateLimit(key = "auth.forgotPassword", perMinute = 1, perHour = 5, perDay = 20)
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {

        String code = authService.forgotPassword(req);

        if (code != null) {
            // Development mode — return code in response so the mobile app can auto-fill it
            return ResponseEntity.ok(Map.of(
                    "message", "Reset code generated (dev mode)",
                    "code", code));
        }
        return ResponseEntity.ok(Map.of("message", "If an account exists, a reset code has been sent"));
    }

    @PostMapping("/verify-reset-code")
    @RateLimit(key = "auth.verifyResetCode", perMinute = 5, perHour = 10)
    public ResponseEntity<Map<String, String>> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest req) {

        authService.verifyResetCode(req);
        return ResponseEntity.ok(Map.of("message", "Code verified successfully"));
    }

    @PostMapping("/reset-password")
    @RateLimit(key = "auth.resetPassword", perMinute = 3, perHour = 5)
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {

        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /**
     * Authenticated endpoint — changes the current user's password.
     * Requires the caller to provide their existing password for verification.
     * Unlike /reset-password, no email reset code is needed.
     */
    @PostMapping("/change-password")
    @RateLimit(key = "auth.changePassword", perMinute = 3, perHour = 10)
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
