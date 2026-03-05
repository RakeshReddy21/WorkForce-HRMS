package org.example.workforce.controller;

import org.example.workforce.dto.*;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/employees/attendance")
public class EmployeeAttendanceController {
    @Autowired
    private AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse> checkIn(@RequestBody(required = false) CheckInRequest request,
                                                HttpServletRequest httpRequest) {
        String email = getCurrentUserEmail();
        String ipAddress = getClientIp(httpRequest);
        AttendanceResponse response = attendanceService.checkIn(email, request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse(true, "Checked in successfully", response));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse> checkOut(@RequestBody(required = false) CheckOutRequest request,
                                                 HttpServletRequest httpRequest) {
        String email = getCurrentUserEmail();
        String ipAddress = getClientIp(httpRequest);
        AttendanceResponse response = attendanceService.checkOut(email, request, ipAddress);
        return ResponseEntity.ok(new ApiResponse(true, "Checked out successfully", response));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse> getTodayStatus() {
        String email = getCurrentUserEmail();
        AttendanceResponse response = attendanceService.getTodayStatus(email);
        return ResponseEntity.ok(new ApiResponse(true, "Today's attendance status fetched", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse> getMyAttendanceHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "attendanceDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String email = getCurrentUserEmail();
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AttendanceResponse> history = attendanceService.getMyAttendance(email, startDate, endDate, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Attendance history fetched", history));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse> getMySummary(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        String email = getCurrentUserEmail();
        AttendanceSummaryResponse summary = attendanceService.getMySummary(email, month, year);
        return ResponseEntity.ok(new ApiResponse(true, "Attendance summary fetched", summary));
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return authentication.getName();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
