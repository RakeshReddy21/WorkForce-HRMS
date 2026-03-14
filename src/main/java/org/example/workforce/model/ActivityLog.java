package org.example.workforce.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log", indexes = {
        @Index(name = "idx_log_entity", columnList = "entity_type"),
        @Index(name = "idx_log_created", columnList = "created_at"),
        @Index(name = "idx_log_action", columnList = "action"),
        @Index(name = "idx_log_ip", columnList = "ip_address")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"performedBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    @EqualsAndHashCode.Include
    private Integer logId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "performed_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Employee performedBy;
    @Column(nullable = false, length = 200)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @Column(name = "entity_id")
    private Integer entityId;
    @Column(columnDefinition = "TEXT")
    private String details;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "status", length = 20)
    private String status;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
