package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.AttendanceResponse;
import org.example.workforce.dto.AttendanceSummaryResponse;
import org.example.workforce.service.AttendanceService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeAttendanceController.class)
class EmployeeAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AttendanceService attendanceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testCheckIn() throws Exception {
        AttendanceResponse response = new AttendanceResponse();
        when(attendanceService.checkIn(eq("emp@test.com"), any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/employees/attendance/check-in").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Checked in successfully"));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testCheckOut() throws Exception {
        AttendanceResponse response = new AttendanceResponse();
        when(attendanceService.checkOut(eq("emp@test.com"), any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/employees/attendance/check-out").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Checked out successfully"));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetTodayStatus() throws Exception {
        AttendanceResponse response = new AttendanceResponse();
        when(attendanceService.getTodayStatus("emp@test.com")).thenReturn(response);

        mockMvc.perform(get("/api/employees/attendance/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyAttendanceHistory() throws Exception {
        Page<AttendanceResponse> page = new PageImpl<>(Collections.emptyList());
        when(attendanceService.getMyAttendance(eq("emp@test.com"), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/attendance/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMySummary() throws Exception {
        AttendanceSummaryResponse summary = new AttendanceSummaryResponse();
        when(attendanceService.getMySummary(eq("emp@test.com"), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/employees/attendance/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

