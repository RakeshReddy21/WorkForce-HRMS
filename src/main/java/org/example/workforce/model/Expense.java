package org.example.workforce.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.example.workforce.model.enums.ExpenseCategory;
import org.example.workforce.model.enums.ExpenseStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense", indexes = {
        @Index(name = "idx_expense_emp", columnList = "employee_id"),
        @Index(name = "idx_expense_status", columnList = "status"),
        @Index(name = "idx_expense_date", columnList = "expense_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"employee", "actionedBy", "financeActionedBy", "items"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    @EqualsAndHashCode.Include
    private Integer expenseId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee employee;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ExpenseCategory category = ExpenseCategory.OTHER;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "receipt_file_name", length = 255)
    private String receiptFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    // Manager approval
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actioned_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee actionedBy;

    @Column(name = "manager_comments", columnDefinition = "TEXT")
    private String managerComments;

    @Column(name = "manager_action_date")
    private LocalDateTime managerActionDate;

    // Finance approval
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "finance_actioned_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee financeActionedBy;

    @Column(name = "finance_comments", columnDefinition = "TEXT")
    private String financeComments;

    @Column(name = "finance_action_date")
    private LocalDateTime financeActionDate;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "reimbursed_date")
    private LocalDateTime reimbursedDate;

    // AI-parsed fields stored for audit
    @Column(name = "ai_parsed", columnDefinition = "TEXT")
    private String aiParsedData;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExpenseItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper method
    public void addItem(ExpenseItem item) {
        items.add(item);
        item.setExpense(this);
    }
}

