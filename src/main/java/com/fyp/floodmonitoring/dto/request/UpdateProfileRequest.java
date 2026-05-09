package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial-update request for PATCH /profile.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 50)  String phone,
        @Size(max = 255) String locationLabel,
        // Accepts a public URL OR a `data:image/...;base64,...` URL produced by the
        // in-app uploader (256x256 JPEG q=0.85 → roughly 60-80 KB after base64).
        // The ceiling is generous so a slightly larger upload still fits.
        @Size(max = 200_000) String avatarUrl
) {}
