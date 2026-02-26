package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.ManagerFeedbackRequest;
import org.example.workforce.dto.ManagerGoalCommentRequest;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.Goal;
import org.example.workforce.model.PerformanceReview;
import org.example.workforce.model.enums.ReviewStatus;
import org.example.workforce.service.PerformanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager")
public class ManagerPerformanceController {
    @Autowired
    private PerformanceService performanceService;

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse> getTeamReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String email = getManagerEmail();
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PerformanceReview> reviews = performanceService.getTeamReviews(email, status, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Team reviews fetched successfully", reviews));
    }

    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse> getTeamReviewById(@PathVariable Integer reviewId) {
        String email = getManagerEmail();
        PerformanceReview review = performanceService.getTeamReviewById(email, reviewId);
        return ResponseEntity.ok(new ApiResponse(true, "Review fetched successfully", review));
    }

    @PatchMapping("/reviews/{reviewId}/feedback")
    public ResponseEntity<ApiResponse> provideReviewFeedback(@PathVariable Integer reviewId, @Valid @RequestBody ManagerFeedbackRequest request) {
        String email = getManagerEmail();
        PerformanceReview review = performanceService.provideReviewFeedback(email, reviewId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Feedback submitted successfully", review));
    }

    @GetMapping("/goals")
    public ResponseEntity<ApiResponse> getAllTeamGoals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String email = getManagerEmail();
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Goal> goals = performanceService.getAllTeamGoals(email, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Team goals fetched successfully", goals));
    }

    @GetMapping("/goals/{employeeCode}")
    public ResponseEntity<ApiResponse> getTeamMemberGoals(
            @PathVariable String employeeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String email = getManagerEmail();
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Goal> goals = performanceService.getTeamMemberGoals(email, employeeCode, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Team member goals fetched successfully", goals));
    }

    @PatchMapping("/goals/{goalId}/comment")
    public ResponseEntity<ApiResponse> commentOnGoal(@PathVariable Integer goalId, @Valid @RequestBody ManagerGoalCommentRequest request) {
        String email = getManagerEmail();
        Goal goal = performanceService.commentOnGoal(email, goalId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Comment added to goal successfully", goal));
    }

    private String getManagerEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Manager not authenticated");
        }
        return auth.getName();
    }
}
