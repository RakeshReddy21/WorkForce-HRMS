package org.example.workforce.service;

import org.example.workforce.dto.AnnouncementRequest;
import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Announcement;
import org.example.workforce.model.Employee;
import org.example.workforce.repository.AnnouncementRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnnouncementService {
    @Autowired
    private AnnouncementRepository announcementRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private NotificationService notificationService;

    @Transactional
    public Announcement createAnnouncement(String adminEmail, AnnouncementRequest request) {
        Employee admin = employeeRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with email: " + adminEmail));
        Announcement announcement = Announcement.builder()
                .title(request.getTitle()).content(request.getContent()).createdBy(admin).build();
        Announcement saved = announcementRepository.save(announcement);
        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() && !e.getEmployeeId().equals(admin.getEmployeeId()))
                .toList();
        for (Employee emp : activeEmployees) {
            notificationService.notifyAnnouncement(emp, saved.getAnnouncementId(), saved.getTitle());
        }
        return saved;
    }

    public Announcement updateAnnouncement(Integer announcementId, AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        return announcementRepository.save(announcement);
    }

    public Announcement deactivateAnnouncement(Integer announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
        if (!announcement.getIsActive()) {
            throw new InvalidActionException("Announcement is already deactivated");
        }
        announcement.setIsActive(false);
        return announcementRepository.save(announcement);
    }

    public Announcement activateAnnouncement(Integer announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
        if (announcement.getIsActive()) {
            throw new InvalidActionException("Announcement is already active");
        }
        announcement.setIsActive(true);
        return announcementRepository.save(announcement);
    }

    public Page<Announcement> getActiveAnnouncements(Pageable pageable) {
        return announcementRepository.findByIsActiveOrderByCreatedAtDesc(true, pageable);
    }

    public Page<Announcement> getAllAnnouncements(Pageable pageable) {
        return announcementRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Announcement getAnnouncementById(Integer announcementId) {
        return announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
    }
}
