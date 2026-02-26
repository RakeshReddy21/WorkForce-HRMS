package org.example.workforce.service;

import org.example.workforce.model.ActivityLog;
import org.example.workforce.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityLogService {
    @Autowired
    private ActivityLogRepository activityLogRepository;

    public Page<ActivityLog> getAllLogs(Pageable pageable) {
        return activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<ActivityLog> getLogsByEntityType(String entityType, Pageable pageable) {
        return activityLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType, pageable);
    }

    public Page<ActivityLog> getLogsByEmployee(Integer employeeId, Pageable pageable) {
        return activityLogRepository.findByPerformedBy_EmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
    }

    public List<ActivityLog> getLogsByEntity(String entityType, Integer entityId) {
        return activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    public Page<ActivityLog> getLogsByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        return activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime, pageable);
    }
}
