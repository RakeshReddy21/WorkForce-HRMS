package org.example.workforce.controller;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.DashboardResponse;
import org.example.workforce.dto.EmployeeReportResponse;
import org.example.workforce.dto.LeaveReportResponse;
import org.example.workforce.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    @Autowired
    private DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse> getDashboard() {
        DashboardResponse dashboard = dashboardService.getDashboard();
        return ResponseEntity.ok(new ApiResponse(true, "Dashboard data fetched successfully", dashboard));
    }

    @GetMapping("/leave-report")
    public ResponseEntity<ApiResponse> getLeaveReport(@RequestParam(required = false) Integer year) {
        List<LeaveReportResponse> report = dashboardService.getLeaveReport(year);
        return ResponseEntity.ok(new ApiResponse(true, "Leave report fetched successfully", report));
    }

    @GetMapping("/leave-report/department/{departmentId}")
    public ResponseEntity<ApiResponse> getLeaveReportByDepartment(
            @PathVariable Integer departmentId,
            @RequestParam(required = false) Integer year) {
        List<LeaveReportResponse> report = dashboardService.getLeaveReportByDepartment(departmentId, year);
        return ResponseEntity.ok(new ApiResponse(true, "Department leave report fetched successfully", report));
    }

    @GetMapping("/leave-report/employee/{employeeCode}")
    public ResponseEntity<ApiResponse> getLeaveReportByEmployee(
            @PathVariable String employeeCode,
            @RequestParam(required = false) Integer year) {
        List<LeaveReportResponse> report = dashboardService.getLeaveReportByEmployee(employeeCode, year);
        return ResponseEntity.ok(new ApiResponse(true, "Employee leave report fetched successfully", report));
    }

    @GetMapping("/employee-report")
    public ResponseEntity<ApiResponse> getEmployeeReport() {
        EmployeeReportResponse report = dashboardService.getEmployeeReport();
        return ResponseEntity.ok(new ApiResponse(true, "Employee report generated successfully", report));
    }
}
