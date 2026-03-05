package org.example.workforce.service;

import org.example.workforce.exception.AccessDeniedException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.Notification;
import org.example.workforce.model.enums.NotificationType;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.repository.NotificationRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private WebSocketNotificationService wsNotificationService;

    @InjectMocks
    private NotificationService notificationService;

    private Employee employee;
    private Employee manager;
    private Notification notification;

    @BeforeEach
    void setUp() {
        manager = Employee.builder()
                .employeeId(2)
                .email("manager@test.com")
                .firstName("Manager")
                .lastName("User")
                .role(Role.MANAGER)
                .isActive(true)
                .build();

        employee = Employee.builder()
                .employeeId(1)
                .email("emp@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .manager(manager)
                .build();

        notification = Notification.builder()
                .notificationId(1)
                .recipient(employee)
                .title("Test Notification")
                .message("Test message")
                .type(NotificationType.LEAVE_APPLIED)
                .isRead(false)
                .build();
    }

    @Test
    void sendNotification_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(5L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.sendNotification(employee, "Test", "Message",
                NotificationType.LEAVE_APPLIED, 1, "LEAVE_APPLICATION");

        verify(notificationRepository).save(any(Notification.class));
        verify(wsNotificationService).pushNotification(eq("emp@test.com"), anyMap());
    }

    @Test
    void sendNotification_WebSocketFailure_DoesNotThrow() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(1L);
        doThrow(new RuntimeException("WebSocket error"))
                .when(wsNotificationService).pushNotification(anyString(), anyMap());

        assertDoesNotThrow(() -> notificationService.sendNotification(
                employee, "Test", "Message", NotificationType.LEAVE_APPLIED, 1, "LEAVE_APPLICATION"));
    }

    @Test
    void getMyNotifications_All_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.findByRecipient_EmployeeIdOrderByCreatedAtDesc(1, pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<Notification> result = notificationService.getMyNotifications("emp@test.com", null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMyNotifications_FilterByReadStatus_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.findByRecipient_EmployeeIdAndIsReadOrderByCreatedAtDesc(1, false, pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<Notification> result = notificationService.getMyNotifications("emp@test.com", false, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMyNotifications_EmployeeNotFound_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.getMyNotifications("unknown@test.com", null, pageable));
    }

    @Test
    void getUnreadCount_Success() {
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(3L);

        long result = notificationService.getUnreadCount("emp@test.com");

        assertEquals(3L, result);
    }

    @Test
    void markAsRead_Success() {
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.findById(1)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.markAsRead("emp@test.com", 1);

        assertNotNull(result);
        assertTrue(result.getIsRead());
    }

    @Test
    void markAsRead_NotOwner_ThrowsException() {
        Notification otherNotif = Notification.builder()
                .notificationId(2)
                .recipient(manager)
                .title("Other")
                .message("Other msg")
                .type(NotificationType.LEAVE_APPLIED)
                .isRead(false)
                .build();

        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.findById(2)).thenReturn(Optional.of(otherNotif));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markAsRead("emp@test.com", 2));
    }

    @Test
    void markAsRead_NotificationNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead("emp@test.com", 99));
    }

    @Test
    void markAllAsRead_Success() {
        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(notificationRepository.markAllAsRead(1)).thenReturn(5);

        int result = notificationService.markAllAsRead("emp@test.com");

        assertEquals(5, result);
    }

    @Test
    void notifyLeaveApplied_WithManager_NotifiesBothManagerAndAdmins() {
        Employee admin = Employee.builder()
                .employeeId(3).email("admin@test.com")
                .firstName("Admin").lastName("User")
                .role(Role.ADMIN).isActive(true).build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(anyInt(), eq(false))).thenReturn(1L);
        when(employeeRepository.findByRoleAndIsActive(Role.ADMIN, true)).thenReturn(List.of(admin));
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveApplied(employee);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void notifyLeaveApplied_NoManager_OnlyAdminsNotified() {
        employee.setManager(null);
        Employee admin = Employee.builder()
                .employeeId(3).email("admin@test.com")
                .firstName("Admin").lastName("User")
                .role(Role.ADMIN).isActive(true).build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(anyInt(), eq(false))).thenReturn(1L);
        when(employeeRepository.findByRoleAndIsActive(Role.ADMIN, true)).thenReturn(List.of(admin));
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveApplied(employee);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void notifyLeaveApplied_ManagerIsAdmin_NoDuplicate() {
        Employee adminManager = Employee.builder()
                .employeeId(2).email("adminmgr@test.com")
                .firstName("AdminMgr").lastName("User")
                .role(Role.ADMIN).isActive(true).build();
        employee.setManager(adminManager);

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(anyInt(), eq(false))).thenReturn(1L);
        when(employeeRepository.findByRoleAndIsActive(Role.ADMIN, true)).thenReturn(List.of(adminManager));
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveApplied(employee);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void notifyLeaveApproved_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(1L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveApproved(employee, 100);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyLeaveRejected_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(1L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveRejected(employee, 100);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyLeaveCancelled_WithManager_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(2, false)).thenReturn(1L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyLeaveCancelled(employee, 100);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyLeaveCancelled_NoManager_NoNotification() {
        employee.setManager(null);

        notificationService.notifyLeaveCancelled(employee, 100);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void notifyReviewSubmitted_WithManager_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(2, false)).thenReturn(1L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyReviewSubmitted(employee, 200);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyReviewSubmitted_NoManager_NoNotification() {
        employee.setManager(null);

        notificationService.notifyReviewSubmitted(employee, 200);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void notifyAnnouncement_Success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(1L);
        doNothing().when(wsNotificationService).pushNotification(anyString(), anyMap());

        notificationService.notifyAnnouncement(employee, 300, "Company Picnic");

        verify(notificationRepository).save(any(Notification.class));
    }
}
