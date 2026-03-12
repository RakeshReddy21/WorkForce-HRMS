package org.example.workforce.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseActionRequest {
    private String action;      // APPROVED, REJECTED
    private String comments;
}

