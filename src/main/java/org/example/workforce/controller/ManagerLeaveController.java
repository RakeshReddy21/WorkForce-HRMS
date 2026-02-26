package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.LeaveActionRequest;
import org.example.workforce.dto.TeamLeaveCalendarEntry;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/manager/leaves")
public class ManagerLeaveController {
    @Autowired
    private LeaveService leaveService;

    @GetMapping("/team")
    public ResponseEntity<ApiResponse> getTeamLeaves(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "appliedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String email = getManagerEmail();
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LeaveApplication> leaves = leaveService.getTeamLeaves(email, status, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Team leaves fetched successfully", leaves));
    }

    @PatchMapping("/{leaveId}/action")
    public ResponseEntity<ApiResponse> actionLeave(@PathVariable Integer leaveId, @Valid @RequestBody LeaveActionRequest request) {
        String email = getManagerEmail();
        LeaveApplication leave = leaveService.actionLeave(email, leaveId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Leave " + leave.getStatus().name().toLowerCase() + " successfully", leave));
    }

    @GetMapping("/team/calendar")
    public ResponseEntity<ApiResponse> getTeamLeaveCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String email = getManagerEmail();
        List<TeamLeaveCalendarEntry> calendar = leaveService.getTeamLeaveCalendar(email, startDate, endDate);
        return ResponseEntity.ok(new ApiResponse(true, "Team leave calendar fetched successfully", calendar));
    }

    @GetMapping("/team/{employeeCode}/balance")
    public ResponseEntity<ApiResponse> getTeamMemberBalance(@PathVariable String employeeCode) {
        String email = getManagerEmail();
        List<LeaveBalance> balance = leaveService.getTeamMemberBalance(email, employeeCode);
        return ResponseEntity.ok(new ApiResponse(true, "Team member balance fetched successfully", balance));
    }

    private String getManagerEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Manager not authenticated");
        }
        return auth.getName();
    }
}
