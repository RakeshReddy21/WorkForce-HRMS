package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.DashboardResponse;
import org.example.workforce.dto.EmployeeReportResponse;
import org.example.workforce.dto.LeaveReportResponse;
import org.example.workforce.service.DashboardService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetDashboard() throws Exception {
        DashboardResponse response = new DashboardResponse();
        when(dashboardService.getDashboard()).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dashboard data fetched successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLeaveReport() throws Exception {
        when(dashboardService.getLeaveReport(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/dashboard/leave-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLeaveReportWithYear() throws Exception {
        when(dashboardService.getLeaveReport(eq(2026))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/dashboard/leave-report").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLeaveReportByDepartment() throws Exception {
        when(dashboardService.getLeaveReportByDepartment(eq(1), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/dashboard/leave-report/department/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Department leave report fetched successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLeaveReportByEmployee() throws Exception {
        when(dashboardService.getLeaveReportByEmployee(eq("EMP001"), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/dashboard/leave-report/employee/EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee leave report fetched successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetEmployeeReport() throws Exception {
        EmployeeReportResponse report = new EmployeeReportResponse();
        when(dashboardService.getEmployeeReport()).thenReturn(report);

        mockMvc.perform(get("/api/admin/dashboard/employee-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee report generated successfully"));
    }
}


















