package org.example.workforce.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseRequest {
    private String title;
    private String description;
    private String category;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDate expenseDate;
    private String vendorName;
    private String invoiceNumber;
    private String receiptBase64;       // base64-encoded receipt image
    private String receiptFileName;
    private List<ExpenseItemRequest> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExpenseItemRequest {
        private String description;
        private BigDecimal amount;
        private Integer quantity;
    }
}

