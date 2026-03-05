package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.ManagerFeedbackRequest;
import org.example.workforce.exception.UnauthorizedException;
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
@RequestMapping("/api/admin/performance")
public class AdminPerformanceController {

    @Autowired
    private PerformanceService performanceService;

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse> getAllReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PerformanceReview> reviews = performanceService.getAllReviews(status, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "All reviews fetched successfully", reviews));
    }

    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse> getReviewById(@PathVariable Integer reviewId) {
        PerformanceReview review = performanceService.getAdminReviewById(reviewId);
        return ResponseEntity.ok(new ApiResponse(true, "Review fetched successfully", review));
    }

    @PatchMapping("/reviews/{reviewId}/feedback")
    public ResponseEntity<ApiResponse> provideReviewFeedback(
            @PathVariable Integer reviewId,
            @Valid @RequestBody ManagerFeedbackRequest request) {
        String email = getAdminEmail();
        PerformanceReview review = performanceService.provideAdminReviewFeedback(email, reviewId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Feedback submitted successfully", review));
    }

    private String getAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Admin not authenticated");
        }
        return auth.getName();
    }
}
