package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.AttendanceResponse;
import org.example.workforce.service.AttendanceService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerAttendanceController.class)
class ManagerAttendanceControllerTest {

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
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamAttendanceToday() throws Exception {
        when(attendanceService.getTeamAttendanceToday("mgr@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/manager/attendance/team/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Team attendance for today fetched"));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamAttendance() throws Exception {
        when(attendanceService.getTeamAttendanceBetween(eq("mgr@test.com"), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/manager/attendance/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Team attendance fetched"));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamAttendanceWithDates() throws Exception {
        when(attendanceService.getTeamAttendanceBetween(eq("mgr@test.com"), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/manager/attendance/team")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

