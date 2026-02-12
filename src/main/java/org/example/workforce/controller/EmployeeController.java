package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.dto.UpdateProfileRequest;
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
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getMyProfile() {
        try {
            String email = getCurrentUserEmail();
            EmployeeProfileResponse profile = employeeService.getEmployeeProfileByEmail(email);
            return ResponseEntity.ok(new ApiResponse(true, "Profile fetched successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @PutMapping("/me")
    public ResponseEntity<ApiResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            String email = getCurrentUserEmail();
            EmployeeProfileResponse profile = employeeService.updateProfileWithResponse(email, request);
            return ResponseEntity.ok(new ApiResponse(true, "Profile updated successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
