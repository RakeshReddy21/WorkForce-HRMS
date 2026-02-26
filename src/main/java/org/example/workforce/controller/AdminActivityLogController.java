package org.example.workforce.controller;

import org.example.workforce.dto.ApiResponse;
import org.example.workforce.model.ActivityLog;
import org.example.workforce.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/activity-logs")
public class AdminActivityLogController {
    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logs = activityLogService.getAllLogs(pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Activity logs fetched successfully", logs));
    }

    @GetMapping("/entity-type/{entityType}")
    public ResponseEntity<ApiResponse> getLogsByEntityType(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logs = activityLogService.getLogsByEntityType(entityType, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Activity logs fetched successfully", logs));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse> getLogsByEmployee(
            @PathVariable Integer employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logs = activityLogService.getLogsByEmployee(employeeId, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Activity logs fetched successfully", logs));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse> getLogsByEntity(@PathVariable String entityType, @PathVariable Integer entityId) {
        List<ActivityLog> logs = activityLogService.getLogsByEntity(entityType, entityId);
        return ResponseEntity.ok(new ApiResponse(true, "Activity logs fetched successfully", logs));
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logs = activityLogService.getLogsByDateRange(startDate, endDate, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Activity logs fetched successfully", logs));
    }
}
