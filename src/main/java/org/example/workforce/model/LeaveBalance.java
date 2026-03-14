package org.example.workforce.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_emp_leave_year", columnNames = {"employee_id", "leave_type_id", "`year`"})
}, indexes = {@Index(name = "idx_balance_year", columnList = "`year`")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"employee", "leaveType", "adjustedBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "balance_id")
    @EqualsAndHashCode.Include
    private Integer balanceId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee employee;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LeaveType leaveType;
    @Column(name = "`year`", nullable = false)
    private Integer year;
    @Column(name = "total_leaves")
    @Builder.Default
    private Integer totalLeaves = 0;
    @Column(name = "used_leaves")
    @Builder.Default
    private Integer usedLeaves = 0;
    @Transient
    public Integer getAvailableBalance() {
        return (totalLeaves != null ? totalLeaves : 0) - (usedLeaves != null ? usedLeaves : 0);
    }
    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "adjusted_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee adjustedBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
