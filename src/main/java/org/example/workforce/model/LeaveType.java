package org.example.workforce.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name="leave_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_type_id")
    private Integer leaveTypeId;
    @Column(name = "leave_type_name", nullable = false, unique = true, length = 50)
    private String leaveTypeName;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "default_days")
    @Builder.Default
    private Integer defaultDays = 0;
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
