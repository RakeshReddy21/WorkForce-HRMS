package org.example.workforce.repository;
import org.example.workforce.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Integer> {
    List<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Integer entityId);

    List<ActivityLog> findByPerformedBy_EmployeeIdOrderByCreatedAtDesc(Integer employeeId);
}
