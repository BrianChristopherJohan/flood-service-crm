package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.CreateAdminUserRequest;
import com.fyp.floodmonitoring.dto.request.UpdateAdminUserRequest;
import com.fyp.floodmonitoring.dto.response.AdminUserDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.AdminUserService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AdminUserController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("AdminUserController Tests")
class AdminUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AdminUserService adminUserService;

    private AdminUserDto sampleAdmin;
    private AdminUserDto sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleAdmin = new AdminUserDto(
            "admin-uuid-001", "Admin User", "admin@example.com",
            "Admin", "active", "2024-01-01T00:00:00Z", "2025-01-01T10:00:00Z"
        );
        sampleCustomer = new AdminUserDto(
            "user-uuid-001", "John Doe", "john@example.com",
            "Customer", "active", "2024-02-01T00:00:00Z", "2025-01-01T09:00:00Z"
        );
    }

    @Nested
    @DisplayName("GET /admin/users")
    class ListUsers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with all users for admin")
        void listUsers_Admin_Returns200() throws Exception {
            when(adminUserService.listAllUsers()).thenReturn(List.of(sampleAdmin, sampleCustomer));

            mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[0].role").value("Admin"))
                .andExpect(jsonPath("$[1].email").value("john@example.com"))
                .andExpect(jsonPath("$[1].role").value("Customer"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin user")
        void listUsers_Customer_Returns403() throws Exception {
            mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 401 for unauthenticated request")
        void listUsers_NoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /admin/users")
    class CreateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with created user for admin")
        void createUser_Admin_Returns200() throws Exception {
            when(adminUserService.createUser(any())).thenReturn(sampleCustomer);

            CreateAdminUserRequest req = new CreateAdminUserRequest(
                "John", "Doe", "john@example.com", "Password@123", "customer"
            );

            mockMvc.perform(post("/admin/users")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("Customer"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin")
        void createUser_Customer_Returns403() throws Exception {
            CreateAdminUserRequest req = new CreateAdminUserRequest(
                "John", "Doe", "john@example.com", "Password@123", "customer"
            );

            mockMvc.perform(post("/admin/users")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /admin/users/{id}")
    class UpdateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with updated user")
        void updateUser_Admin_Returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            AdminUserDto updated = new AdminUserDto(
                userId.toString(), "John Updated", "john@example.com",
                "Admin", "active", "2024-02-01T00:00:00Z", "2025-01-01T09:00:00Z"
            );
            when(adminUserService.updateUser(eq(userId), any(UUID.class), any())).thenReturn(updated);

            UpdateAdminUserRequest req = new UpdateAdminUserRequest("John", "Updated", "admin", null);

            mockMvc.perform(patch("/admin/users/" + userId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Admin"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/users/{id}")
    class DeleteUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 on successful delete")
        void deleteUser_Admin_Returns204() throws Exception {
            UUID userId = UUID.randomUUID();
            doNothing().when(adminUserService).deleteUser(eq(userId), any(UUID.class));

            mockMvc.perform(delete("/admin/users/" + userId).with(csrf()))
                .andExpect(status().isNoContent());

            verify(adminUserService).deleteUser(eq(userId), any(UUID.class));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin")
        void deleteUser_Customer_Returns403() throws Exception {
            mockMvc.perform(delete("/admin/users/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
        }
    }
}
