package org.example.workforce.controller;

import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.AttendanceResponse;
import org.example.workforce.dto.AttendanceSummaryResponse;
import org.example.workforce.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {
    @Autowired
    private AttendanceService attendanceService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllAttendanceByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "attendanceDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AttendanceResponse> attendance = attendanceService.getAllAttendanceByDate(date, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Attendance records fetched", attendance));
    }

    @GetMapping("/{employeeCode}/summary")
    public ResponseEntity<ApiResponse> getEmployeeAttendanceSummary(
            @PathVariable String employeeCode,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        AttendanceSummaryResponse summary = attendanceService.getEmployeeSummary(employeeCode, month, year);
        return ResponseEntity.ok(new ApiResponse(true, "Employee attendance summary fetched", summary));
    }

}
