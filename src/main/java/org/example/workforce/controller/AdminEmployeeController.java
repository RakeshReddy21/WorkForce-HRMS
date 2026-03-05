package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.*;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.Employee;
import org.example.workforce.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employees")
public class AdminEmployeeController {
    @Autowired
    private EmployeeService employeeService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerEmployee(@Valid @RequestBody RegisterEmployeeRequest request) {
        Employee employee = employeeService.registerEmployee(request);
        EmployeeProfileResponse profile = employeeService.getEmployeeByCode(employee.getEmployeeCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Employee registered successfully", profile));
    }

    @GetMapping("/{employeeCode}")
    public ResponseEntity<ApiResponse> getEmployee(@PathVariable String employeeCode) {
        EmployeeProfileResponse profile = employeeService.getEmployeeByCode(employeeCode);
        return ResponseEntity.ok(new ApiResponse(true, "Employee fetched successfully", profile));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "employeeId") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<EmployeeProfileResponse> employees = employeeService.getEmployees(keyword, departmentId, role, active, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Employees fetched successfully", employees));
    }

    @PutMapping("/{employeeCode}")
    public ResponseEntity<ApiResponse> updateEmployee(@PathVariable String employeeCode, @Valid @RequestBody UpdateEmployeeRequest request) {
        String adminEmail = getAdminEmail();
        EmployeeProfileResponse profile = employeeService.updateEmployeeByAdmin(employeeCode, request, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Employee updated successfully", profile));
    }

    @PatchMapping("/{employeeCode}/deactivate")
    public ResponseEntity<ApiResponse> deactivateEmployee(@PathVariable String employeeCode) {
        String adminEmail = getAdminEmail();
        EmployeeProfileResponse profile = employeeService.deactivateEmployee(employeeCode, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Employee deactivated successfully", profile));
    }

    @PatchMapping("/{employeeCode}/activate")
    public ResponseEntity<ApiResponse> activateEmployee(@PathVariable String employeeCode) {
        String adminEmail = getAdminEmail();
        EmployeeProfileResponse profile = employeeService.activateEmployee(employeeCode, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Employee reactivated successfully", profile));
    }

    @PatchMapping("/{employeeCode}/manager")
    public ResponseEntity<ApiResponse> assignManager(@PathVariable String employeeCode, @Valid @RequestBody AssignManagerRequest request) {
        String adminEmail = getAdminEmail();
        EmployeeProfileResponse profile = employeeService.assignManager(employeeCode, request.getManagerCode(), adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Manager assigned successfully", profile));
    }

    @PatchMapping("/{employeeCode}/reset-password")
    public ResponseEntity<ApiResponse> forceResetPassword(@PathVariable String employeeCode,
                                                           @Valid @RequestBody ForceResetPasswordRequest request) {
        String adminEmail = getAdminEmail();
        employeeService.forceResetPassword(employeeCode, request.getNewPassword(), adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Password for " + employeeCode + " has been reset successfully"));
    }

    @PatchMapping("/{employeeCode}/enable-2fa")
    public ResponseEntity<ApiResponse> enable2FA(@PathVariable String employeeCode) {
        String adminEmail = getAdminEmail();
        employeeService.enable2FA(employeeCode, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "2FA has been enabled for " + employeeCode));
    }

    @PatchMapping("/{employeeCode}/disable-2fa")
    public ResponseEntity<ApiResponse> disable2FA(@PathVariable String employeeCode) {
        String adminEmail = getAdminEmail();
        employeeService.disable2FA(employeeCode, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "2FA has been disabled for " + employeeCode));
    }

    @PostMapping("/force-enable-2fa")
    public ResponseEntity<ApiResponse> forceEnable2FAForAll() {
        String adminEmail = getAdminEmail();
        int count = employeeService.forceEnable2FAForAll(adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "2FA force-enabled for " + count + " employees"));
    }

    private String getAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Admin not authenticated");
        }
        return auth.getName();
    }
}
