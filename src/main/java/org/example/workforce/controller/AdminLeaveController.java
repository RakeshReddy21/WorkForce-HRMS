package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.AdjustLeaveBalanceRequest;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.HolidayRequest;
import org.example.workforce.dto.LeaveActionRequest;
import org.example.workforce.dto.LeaveTypeRequest;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.Holiday;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.LeaveType;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.service.LeaveService;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/leaves")
public class AdminLeaveController {
    @Autowired
    private LeaveService leaveService;

    @PostMapping("/types")
    public ResponseEntity<ApiResponse> createLeaveType(@Valid @RequestBody LeaveTypeRequest request) {
        LeaveType leaveType = leaveService.createLeaveType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Leave type created successfully", leaveType));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse> getAllLeaveType() {
        List<LeaveType> types = leaveService.getAllLeaveType();
        return ResponseEntity.ok(new ApiResponse(true, "Leave types fetched successfully", types));
    }

    @PutMapping("/types/{leaveTypeId}")
    public ResponseEntity<ApiResponse> updateLeaveType(@PathVariable Integer leaveTypeId, @Valid @RequestBody LeaveTypeRequest request) {
        LeaveType leaveType = leaveService.updateLeaveType(leaveTypeId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Leave type updated successfully", leaveType));
    }

    @PostMapping("/balance/{employeeCode}")
    public ResponseEntity<ApiResponse> assignLeaveQuota(@PathVariable String employeeCode, @Valid @RequestBody AdjustLeaveBalanceRequest request) {
        String adminEmail = getAdminEmail();
        LeaveBalance balance = leaveService.assignLeaveQuota(employeeCode, request, adminEmail);
        return ResponseEntity.ok(new ApiResponse(true, "Leave quota assigned successfully", balance));
    }

    @GetMapping("/balance/{employeeCode}")
    public ResponseEntity<ApiResponse> getEmployeeLeaveBalance(@PathVariable String employeeCode) {
        List<LeaveBalance> balances = leaveService.getEmployeeBalance(employeeCode);
        return ResponseEntity.ok(new ApiResponse(true, "Employee leave balance fetched successfully", balances));
    }

    @PostMapping("/holidays")
    public ResponseEntity<ApiResponse> createHoliday(@Valid @RequestBody HolidayRequest request) {
        Holiday holiday = leaveService.createHoliday(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Holiday created successfully", holiday));
    }

    @PutMapping("/holidays/{holidayId}")
    public ResponseEntity<ApiResponse> updateHoliday(@PathVariable Integer holidayId, @Valid @RequestBody HolidayRequest request) {
        Holiday holiday = leaveService.updateHoliday(holidayId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Holiday updated successfully", holiday));
    }

    @DeleteMapping("/holidays/{holidayId}")
    public ResponseEntity<ApiResponse> deleteHoliday(@PathVariable Integer holidayId) {
        leaveService.deleteHoliday(holidayId);
        return ResponseEntity.ok(new ApiResponse(true, "Holiday deleted successfully"));
    }

    @GetMapping("/applications")
    public ResponseEntity<ApiResponse> getAllLeaveApplications(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "appliedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LeaveApplication> applications = leaveService.getAllLeaveApplications(status, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "All leave applications fetched successfully", applications));
    }

    @PatchMapping("/{leaveId}/action")
    public ResponseEntity<ApiResponse> actionLeave(@PathVariable Integer leaveId, @Valid @RequestBody LeaveActionRequest request) {
        String adminEmail = getAdminEmail();
        LeaveApplication leave = leaveService.adminActionLeave(adminEmail, leaveId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Leave " + leave.getStatus().name().toLowerCase() + " successfully", leave));
    }

    @GetMapping("/holidays")
    public ResponseEntity<ApiResponse> getHolidays(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(new ApiResponse(true, "Holiday fetched successfully", leaveService.getHolidays(year)));
    }

    private String getAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Admin not authenticated");
        }
        return auth.getName();
    }
}
