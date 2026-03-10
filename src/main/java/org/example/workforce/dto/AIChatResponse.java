package org.example.workforce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChatResponse {
    private String reply;
    private String action;
    private Object actionData;
    private boolean actionPerformed;
    private List<String> quickReplies;
}
