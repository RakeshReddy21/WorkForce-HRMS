package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.*;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.service.DashboardService;
import org.example.workforce.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getMyProfile() {
        String email = getCurrentUserEmail();
        EmployeeProfileResponse profile = employeeService.getEmployeeProfileByEmail(email);
        return ResponseEntity.ok(new ApiResponse(true, "Profile fetched successfully", profile));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse> getMyDashboard() {
        String email = getCurrentUserEmail();
        EmployeeDashboardResponse dashboard = dashboardService.getEmployeeDashboard(email);
        return ResponseEntity.ok(new ApiResponse(true, "Dashboard fetched successfully", dashboard));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String email = getCurrentUserEmail();
        EmployeeProfileResponse profile = employeeService.updateProfileWithResponse(email, request);
        return ResponseEntity.ok(new ApiResponse(true, "Profile updated successfully", profile));
    }

    @PutMapping("/me/change-password")
    public ResponseEntity<ApiResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String email = getCurrentUserEmail();
        employeeService.changePassword(email, request);
        return ResponseEntity.ok(new ApiResponse(true, "Password changed successfully"));
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return authentication.getName();
    }
}
