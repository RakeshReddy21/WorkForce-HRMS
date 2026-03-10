package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.AIChatRequest;
import org.example.workforce.dto.AIChatResponse;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.exception.UnauthorizedException;
import org.example.workforce.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {
    @Autowired
    private AIService aiService;
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse> chat(@Valid @RequestBody AIChatRequest request) {
        String email = getCurrentUserEmail();
        AIChatResponse response = aiService.processMessage(email, request);
        return ResponseEntity.ok(new ApiResponse(true, "AI response generated", response));
    }
    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }
}