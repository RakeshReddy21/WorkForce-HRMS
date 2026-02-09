package org.example.workforce.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.example.workforce.model.enums.GoalPriority;
import org.example.workforce.model.enums.GoalStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "goal", indexes = {
        @Index(name = "idx_goal_emp", columnList = "employee_id"),
        @Index(name = "idx_goal_year", columnList = "year")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Integer goalId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee employee;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false)
    private Integer year;
    @Column(nullable = false)
    private LocalDate deadline;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private GoalPriority priority = GoalPriority.MEDIUM;
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    @Builder.Default
    private GoalStatus status = GoalStatus.NOT_STARTED;
    @Column
    @Builder.Default
    private Integer progress = 0;
    @Column(name = "manager_comments", columnDefinition = "TEXT")
    private String managerComments;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}