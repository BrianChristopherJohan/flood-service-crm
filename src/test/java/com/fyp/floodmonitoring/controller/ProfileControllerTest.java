package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.request.UpdateProfileRequest;
import com.fyp.floodmonitoring.dto.response.UserProfileDto;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.ProfileService;
import com.fyp.floodmonitoring.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link ProfileController}.
 *
 * <p>Uses {@code @WebMvcTest} with a custom {@link TestSecurityConfig} to exercise
 * security rules without requiring a real JWT filter or database.</p>
 */
@WebMvcTest(
        controllers = ProfileController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("ProfileController Tests")
class ProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private ProfileService profileService;

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private UserProfileDto profileDto;

    @BeforeEach
    void setUp() {
        profileDto = TestDataBuilder.buildUserProfileDto();
    }

    // ── GET /profile ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /profile")
    class GetProfile {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with profile data for authenticated user")
        void getProfile_Authenticated_Returns200() throws Exception {
            when(profileService.getProfile(UUID.fromString(USER_ID))).thenReturn(profileDto);

            mockMvc.perform(get("/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(USER_ID))
                    .andExpect(jsonPath("$.email").value("john.doe@test.com"))
                    .andExpect(jsonPath("$.firstName").value("John"))
                    .andExpect(jsonPath("$.lastName").value("Doe"))
                    .andExpect(jsonPath("$.displayName").value("John Doe"))
                    .andExpect(jsonPath("$.role").value("Customer"))
                    .andExpect(jsonPath("$.phone").value("+60111234567"))
                    .andExpect(jsonPath("$.locationLabel").value("Kuching, Sarawak"));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getProfile_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/profile"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("propagates 404 when user not found in service")
        void getProfile_UserNotFound_Returns404() throws Exception {
            when(profileService.getProfile(any(UUID.class)))
                    .thenThrow(AppException.notFound("User not found"));

            mockMvc.perform(get("/profile"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PATCH /profile ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /profile")
    class UpdateProfile {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with updated profile on valid request")
        void updateProfile_ValidRequest_Returns200() throws Exception {
            UserProfileDto updated = new UserProfileDto(
                    USER_ID, "john.doe@test.com", "Jane", "Doe",
                    "Jane Doe", "Customer", "+60199999999", "Kuching", null
            );
            when(profileService.updateProfile(eq(UUID.fromString(USER_ID)), any(UpdateProfileRequest.class)))
                    .thenReturn(updated);

            UpdateProfileRequest req = new UpdateProfileRequest(
                    "Jane", null, "+60199999999", "Kuching", null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Jane"))
                    .andExpect(jsonPath("$.displayName").value("Jane Doe"))
                    .andExpect(jsonPath("$.phone").value("+60199999999"));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when firstName exceeds 100 characters")
        void updateProfile_FirstNameTooLong_Returns400() throws Exception {
            String longName = "A".repeat(101);
            UpdateProfileRequest req = new UpdateProfileRequest(
                    longName, null, null, null, null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when phone exceeds 50 characters")
        void updateProfile_PhoneTooLong_Returns400() throws Exception {
            String longPhone = "+601" + "9".repeat(50);
            UpdateProfileRequest req = new UpdateProfileRequest(
                    null, null, longPhone, null, null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when locationLabel exceeds 255 characters")
        void updateProfile_LocationLabelTooLong_Returns400() throws Exception {
            String longLabel = "L".repeat(256);
            UpdateProfileRequest req = new UpdateProfileRequest(
                    null, null, null, longLabel, null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void updateProfile_Unauthenticated_Returns401() throws Exception {
            UpdateProfileRequest req = new UpdateProfileRequest(
                    "Jane", null, null, null, null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with partial update — only non-null fields applied")
        void updateProfile_PartialUpdate_Returns200() throws Exception {
            UserProfileDto partiallyUpdated = new UserProfileDto(
                    USER_ID, "john.doe@test.com", "John", "Doe",
                    "John Doe", "Customer", null, "Kuching, Sarawak", null
            );
            when(profileService.updateProfile(any(), any())).thenReturn(partiallyUpdated);

            UpdateProfileRequest req = new UpdateProfileRequest(
                    null, null, null, "Kuching, Sarawak", null
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locationLabel").value("Kuching, Sarawak"));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when avatarUrl exceeds 200000 characters")
        void updateProfile_AvatarUrlTooLong_Returns400() throws Exception {
            String longUrl = "https://example.com/" + "x".repeat(200_000);
            UpdateProfileRequest req = new UpdateProfileRequest(
                    null, null, null, null, longUrl
            );

            mockMvc.perform(patch("/profile")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}
