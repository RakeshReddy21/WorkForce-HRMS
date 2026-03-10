package org.example.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequest {
    @NotBlank(message = "Message is required")
    private String message;
    private List<ChatHistoryEntry> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatHistoryEntry{
        private String role;
        private String content;
    }
}
