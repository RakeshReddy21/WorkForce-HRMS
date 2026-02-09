package org.example.workforce.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.example.workforce.model.enums.ReviewStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_review", indexes = {
        @Index(name = "idx_review_emp", columnList = "employee_id"),
        @Index(name = "idx_review_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Integer reviewId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee reviewer;
    @Column(name = "review_period", nullable = false, length = 50)
    private String reviewPeriod;
    @Column(name = "key_deliverables", columnDefinition = "TEXT")
    private String keyDeliverables;
    @Column(columnDefinition = "TEXT")
    private String accomplishments;
    @Column(name = "areas_of_improvement", columnDefinition = "TEXT")
    private String areasOfImprovement;
    @Column(name = "self_assessment_rating")
    private Integer selfAssessmentRating;
    @Column(name = "manager_rating")
    private Integer managerRating;
    @Column(name = "manager_feedback", columnDefinition = "TEXT")
    private String managerFeedback;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.DRAFT;
    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;
    @Column(name = "reviewed_date")
    private LocalDateTime reviewedDate;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}