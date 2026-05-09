package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateAdminUserRequest;
import com.fyp.floodmonitoring.dto.request.UpdateAdminUserRequest;
import com.fyp.floodmonitoring.dto.response.AdminUserDto;
import com.fyp.floodmonitoring.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fyp.floodmonitoring.exception.AppException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only user management endpoints.
 * All routes require a valid JWT with role = 'ADMIN'.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<AdminUserDto>> listUsers() {
        return ResponseEntity.ok(adminUserService.listAllUsers());
    }

    @PostMapping
    public ResponseEntity<AdminUserDto> createUser(@Valid @RequestBody CreateAdminUserRequest req) {
        return ResponseEntity.ok(adminUserService.createUser(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserDto> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminUserRequest req,
            Authentication auth) {
        return ResponseEntity.ok(adminUserService.updateUser(id, requireUserId(auth), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, Authentication auth) {
        adminUserService.deleteUser(id, requireUserId(auth));
        return ResponseEntity.noContent().build();
    }

    private static UUID requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw AppException.unauthorized("Authentication required");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            throw AppException.unauthorized("Authentication required");
        }
    }
}
