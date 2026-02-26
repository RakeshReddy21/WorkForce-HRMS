package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.AnnouncementRequest;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.Announcement;
import org.example.workforce.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {
    @Autowired
    private AnnouncementService announcementService;

    @PostMapping
    public ResponseEntity<ApiResponse> createAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        String adminEmail = getAdminEmail();
        Announcement announcement = announcementService.createAnnouncement(adminEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Announcement created successfully", announcement));
    }

    @PutMapping("/{announcementId}")
    public ResponseEntity<ApiResponse> updateAnnouncement(@PathVariable Integer announcementId, @Valid @RequestBody AnnouncementRequest request) {
        Announcement announcement = announcementService.updateAnnouncement(announcementId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Announcement updated successfully", announcement));
    }

    @PatchMapping("/{announcementId}/deactivate")
    public ResponseEntity<ApiResponse> deactivateAnnouncement(@PathVariable Integer announcementId) {
        Announcement announcement = announcementService.deactivateAnnouncement(announcementId);
        return ResponseEntity.ok(new ApiResponse(true, "Announcement deactivated successfully", announcement));
    }

    @PatchMapping("/{announcementId}/activate")
    public ResponseEntity<ApiResponse> activateAnnouncement(@PathVariable Integer announcementId) {
        Announcement announcement = announcementService.activateAnnouncement(announcementId);
        return ResponseEntity.ok(new ApiResponse(true, "Announcement activated successfully", announcement));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Announcement> announcements = announcementService.getAllAnnouncements(pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Announcements fetched successfully", announcements));
    }

    private String getAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Admin not authenticated");
        }
        return auth.getName();
    }
}
