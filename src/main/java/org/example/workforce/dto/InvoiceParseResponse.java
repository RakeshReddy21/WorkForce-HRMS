package org.example.workforce.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceParseResponse {
    private boolean success;
    private String title;
    private String vendorName;
    private String invoiceNumber;
    private String invoiceDate;
    private BigDecimal totalAmount;
    private String currency;
    private String category;
    private String description;
    private List<ParsedItem> items;
    private String rawText;
    private String errorMessage;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParsedItem {
        private String description;
        private BigDecimal amount;
        private Integer quantity;
    }
}

