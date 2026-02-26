package org.example.workforce.controller;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.model.Announcement;
import org.example.workforce.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/announcements")
public class EmployeeAnnouncementController {
    @Autowired
    private AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<ApiResponse> getActiveAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Announcement> announcements = announcementService.getActiveAnnouncements(pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Announcements fetched successfully", announcements));
    }

    @GetMapping("/{announcementId}")
    public ResponseEntity<ApiResponse> getAnnouncement(@PathVariable Integer announcementId) {
        Announcement announcement = announcementService.getAnnouncementById(announcementId);
        return ResponseEntity.ok(new ApiResponse(true, "Announcement fetched successfully", announcement));
    }
}
