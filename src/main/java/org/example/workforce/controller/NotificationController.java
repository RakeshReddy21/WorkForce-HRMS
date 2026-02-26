package org.example.workforce.controller;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.model.Notification;
import org.example.workforce.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/notifications")
public class NotificationController {
    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse> getMyNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String email = getCurrentUserEmail();
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationService.getMyNotifications(email, isRead, pageable);
        return ResponseEntity.ok(new ApiResponse(true, "Notifications fetched successfully", notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse> getUnreadCount() {
        String email = getCurrentUserEmail();
        long count = notificationService.getUnreadCount(email);
        return ResponseEntity.ok(new ApiResponse(true, "Unread count fetched successfully", count));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse> markAsRead(@PathVariable Integer notificationId) {
        String email = getCurrentUserEmail();
        Notification notification = notificationService.markAsRead(email, notificationId);
        return ResponseEntity.ok(new ApiResponse(true, "Notification marked as read", notification));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse> markAllAsRead() {
        String email = getCurrentUserEmail();
        int count = notificationService.markAllAsRead(email);
        return ResponseEntity.ok(new ApiResponse(true, count + " notification(s) marked as read"));
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }
}
