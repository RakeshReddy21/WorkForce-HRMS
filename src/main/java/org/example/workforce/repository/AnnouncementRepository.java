package org.example.workforce.repository;
import org.example.workforce.model.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {
    Page<Announcement> findByIsActiveOrderByCreatedAtDesc(Boolean isActive, Pageable pageable);
    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Announcement> findByCreatedBy_EmployeeIdOrderByCreatedAtDesc(Integer employeeId, Pageable pageable);
}
