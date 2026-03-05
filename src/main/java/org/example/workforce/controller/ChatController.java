package org.example.workforce.controller;

import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.ChatMessageResponse;
import org.example.workforce.dto.ConversationResponse;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.service.ChatService;
import org.example.workforce.service.PresenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private PresenceService presenceService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse> getMyConversations() {
        String email = getCurrentUserEmail();
        List<ConversationResponse> conversations = chatService.getMyConversations(email);
        return ResponseEntity.ok(new ApiResponse(true, "Conversations fetched successfully", conversations));
    }

    @PostMapping("/conversations/{otherEmployeeId}")
    public ResponseEntity<ApiResponse> getOrCreateConversation(@PathVariable Integer otherEmployeeId) {
        String email = getCurrentUserEmail();
        ConversationResponse conversation = chatService.getOrCreateConversation(email, otherEmployeeId);
        return ResponseEntity.ok(new ApiResponse(true, "Conversation ready", conversation));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String email = getCurrentUserEmail();
        Page<ChatMessageResponse> messages = chatService.getMessages(email, conversationId, page, size);
        return ResponseEntity.ok(new ApiResponse(true, "Messages fetched successfully", messages));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse> markConversationAsRead(@PathVariable Long conversationId) {
        String email = getCurrentUserEmail();
        int count = chatService.markConversationAsRead(email, conversationId);
        return ResponseEntity.ok(new ApiResponse(true, count + " message(s) marked as read"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse> getUnreadCount() {
        String email = getCurrentUserEmail();
        long count = chatService.getTotalUnreadCount(email);
        return ResponseEntity.ok(new ApiResponse(true, "Unread count fetched", count));
    }

    @GetMapping("/online-users")
    public ResponseEntity<ApiResponse> getOnlineUsers() {
        Set<String> onlineEmails = presenceService.getOnlineUsers();
        return ResponseEntity.ok(new ApiResponse(true, "Online users fetched", onlineEmails));
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }
}
