package org.example.workforce.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "leave_application", indexes = {
        @Index(name = "idx_leave_emp", columnList = "employee_id"),
        @Index(name = "idx_leave_status", columnList = "status"),
        @Index(name = "idx_leave_dates", columnList = "start_date, end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Integer leaveId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LeaveType leaveType;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "total_days", nullable = false)
    private Integer totalDays;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;
    @Column(name = "manager_comments", columnDefinition = "TEXT")
    private String managerComments;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actioned_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee actionedBy;
    @Column(name = "applied_date", updatable = false)
    @CreationTimestamp
    private LocalDateTime appliedDate;
    @Column(name = "action_date")
    private LocalDateTime actionDate;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
