package org.example.workforce.service;

import org.example.workforce.dto.AnnouncementRequest;
import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Announcement;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.AnnouncementRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AnnouncementService announcementService;

    private Employee admin;
    private Employee employee1;
    private Employee employee2;
    private Announcement announcement;
    private AnnouncementRequest announcementRequest;

    @BeforeEach
    void setUp() {
        admin = Employee.builder()
                .employeeId(1)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        employee1 = Employee.builder()
                .employeeId(2)
                .email("emp1@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        employee2 = Employee.builder()
                .employeeId(3)
                .email("emp2@test.com")
                .firstName("Jane")
                .lastName("Smith")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        announcement = Announcement.builder()
                .announcementId(1)
                .title("Company Picnic")
                .content("Annual company picnic next Saturday")
                .createdBy(admin)
                .isActive(true)
                .build();

        announcementRequest = new AnnouncementRequest("Company Picnic", "Annual company picnic next Saturday");
    }

    @Test
    void createAnnouncement_Success() {
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);
        when(employeeRepository.findAll()).thenReturn(Arrays.asList(admin, employee1, employee2));
        doNothing().when(notificationService).notifyAnnouncement(any(Employee.class), anyInt(), anyString());

        Announcement result = announcementService.createAnnouncement("admin@test.com", announcementRequest);

        assertNotNull(result);
        assertEquals("Company Picnic", result.getTitle());

        verify(notificationService, times(2)).notifyAnnouncement(any(Employee.class), anyInt(), anyString());
    }

    @Test
    void createAnnouncement_AdminNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> announcementService.createAnnouncement("admin@test.com", announcementRequest));
    }

    @Test
    void createAnnouncement_NoActiveEmployees_NoNotifications() {
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);
        when(employeeRepository.findAll()).thenReturn(List.of(admin));

        announcementService.createAnnouncement("admin@test.com", announcementRequest);

        verify(notificationService, never()).notifyAnnouncement(any(Employee.class), anyInt(), anyString());
    }

    @Test
    void createAnnouncement_InactiveEmployeesSkipped() {
        Employee inactiveEmp = Employee.builder()
                .employeeId(4).email("inactive@test.com").isActive(false).build();
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);
        when(employeeRepository.findAll()).thenReturn(Arrays.asList(admin, employee1, inactiveEmp));

        announcementService.createAnnouncement("admin@test.com", announcementRequest);

        verify(notificationService, times(1)).notifyAnnouncement(any(Employee.class), anyInt(), anyString());
    }

    @Test
    void updateAnnouncement_Success() {
        AnnouncementRequest updateRequest = new AnnouncementRequest("Updated Title", "Updated Content");
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);

        Announcement result = announcementService.updateAnnouncement(1, updateRequest);

        assertNotNull(result);
        verify(announcementRepository).save(any(Announcement.class));
    }

    @Test
    void updateAnnouncement_NotFound_ThrowsException() {
        when(announcementRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> announcementService.updateAnnouncement(99, announcementRequest));
    }

    @Test
    void deactivateAnnouncement_Success() {
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);

        Announcement result = announcementService.deactivateAnnouncement(1);

        assertNotNull(result);
        assertFalse(result.getIsActive());
    }

    @Test
    void deactivateAnnouncement_NotFound_ThrowsException() {
        when(announcementRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> announcementService.deactivateAnnouncement(99));
    }

    @Test
    void deactivateAnnouncement_AlreadyDeactivated_ThrowsException() {
        announcement.setIsActive(false);
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));

        assertThrows(InvalidActionException.class,
                () -> announcementService.deactivateAnnouncement(1));
    }

    @Test
    void activateAnnouncement_Success() {
        announcement.setIsActive(false);
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);

        Announcement result = announcementService.activateAnnouncement(1);

        assertNotNull(result);
        assertTrue(result.getIsActive());
    }

    @Test
    void activateAnnouncement_NotFound_ThrowsException() {
        when(announcementRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> announcementService.activateAnnouncement(99));
    }

    @Test
    void activateAnnouncement_AlreadyActive_ThrowsException() {
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));

        assertThrows(InvalidActionException.class,
                () -> announcementService.activateAnnouncement(1));
    }

    @Test
    void getActiveAnnouncements_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Announcement> page = new PageImpl<>(List.of(announcement));
        when(announcementRepository.findByIsActiveOrderByCreatedAtDesc(true, pageable)).thenReturn(page);

        Page<Announcement> result = announcementService.getActiveAnnouncements(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAllAnnouncements_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Announcement> page = new PageImpl<>(List.of(announcement));
        when(announcementRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

        Page<Announcement> result = announcementService.getAllAnnouncements(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAnnouncementById_Success() {
        when(announcementRepository.findById(1)).thenReturn(Optional.of(announcement));

        Announcement result = announcementService.getAnnouncementById(1);

        assertNotNull(result);
        assertEquals(1, result.getAnnouncementId());
    }

    @Test
    void getAnnouncementById_NotFound_ThrowsException() {
        when(announcementRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> announcementService.getAnnouncementById(99));
    }
}
