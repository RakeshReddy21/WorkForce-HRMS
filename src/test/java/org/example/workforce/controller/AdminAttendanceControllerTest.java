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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAttendanceController.class)
class AdminAttendanceControllerTest {

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

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllAttendanceByDate() throws Exception {
        Page<AttendanceResponse> page = new PageImpl<>(Collections.emptyList());
        when(attendanceService.getAllAttendanceByDate(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Attendance records fetched"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllAttendanceWithDateParam() throws Exception {
        Page<AttendanceResponse> page = new PageImpl<>(Collections.emptyList());
        when(attendanceService.getAllAttendanceByDate(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/attendance").param("date", "2026-03-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetEmployeeAttendanceSummary() throws Exception {
        AttendanceSummaryResponse summary = new AttendanceSummaryResponse();
        when(attendanceService.getEmployeeSummary(eq("EMP001"), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/attendance/EMP001/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee attendance summary fetched"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetEmployeeAttendanceSummaryWithParams() throws Exception {
        AttendanceSummaryResponse summary = new AttendanceSummaryResponse();
        when(attendanceService.getEmployeeSummary(eq("EMP001"), eq(3), eq(2026))).thenReturn(summary);

        mockMvc.perform(get("/api/admin/attendance/EMP001/summary")
                        .param("month", "3").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}


















