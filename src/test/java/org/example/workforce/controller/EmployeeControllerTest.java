package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ChangePasswordRequest;
import org.example.workforce.dto.EmployeeDashboardResponse;
import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.dto.UpdateProfileRequest;
import org.example.workforce.service.DashboardService;
import org.example.workforce.service.EmployeeService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyProfile() throws Exception {
        EmployeeProfileResponse profile = new EmployeeProfileResponse();
        when(employeeService.getEmployeeProfileByEmail("emp@test.com")).thenReturn(profile);

        mockMvc.perform(get("/api/employees/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile fetched successfully"));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyDashboard() throws Exception {
        EmployeeDashboardResponse dashboard = new EmployeeDashboardResponse();
        when(dashboardService.getEmployeeDashboard("emp@test.com")).thenReturn(dashboard);

        mockMvc.perform(get("/api/employees/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dashboard fetched successfully"));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testUpdateMyProfile() throws Exception {
        EmployeeProfileResponse profile = new EmployeeProfileResponse();
        when(employeeService.updateProfileWithResponse(eq("emp@test.com"), any(UpdateProfileRequest.class))).thenReturn(profile);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("9876543210");

        mockMvc.perform(put("/api/employees/me").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testChangePassword() throws Exception {
        doNothing().when(employeeService).changePassword(eq("emp@test.com"), any(ChangePasswordRequest.class));

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("NewPass@123");
        request.setConfirmPassword("NewPass@123");

        mockMvc.perform(put("/api/employees/me/change-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }
}

