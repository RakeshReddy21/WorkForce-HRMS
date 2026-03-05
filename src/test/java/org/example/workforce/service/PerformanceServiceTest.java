package org.example.workforce.service;

import org.example.workforce.dto.GoalRequest;
import org.example.workforce.dto.GoalProgressRequest;
import org.example.workforce.dto.PerformanceReviewRequest;
import org.example.workforce.exception.*;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.*;
import org.example.workforce.repository.*;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock
    private PerformanceReviewRepository reviewRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PerformanceService performanceService;

    private Employee employee;
    private Employee manager;
    private PerformanceReview review;
    private Goal goal;

    @BeforeEach
    void setUp() {
        manager = Employee.builder()
                .employeeId(1)
                .email("manager@test.com")
                .employeeCode("MGR001")
                .role(Role.MANAGER)
                .build();

        employee = Employee.builder()
                .employeeId(2)
                .email("employee@test.com")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .manager(manager)
                .build();

        review = PerformanceReview.builder()
                .reviewId(1)
                .employee(employee)
                .status(ReviewStatus.SUBMITTED)
                .build();

        goal = Goal.builder()
                .goalId(1)
                .employee(employee)
                .title("Complete Project")
                .status(GoalStatus.NOT_STARTED)
                .progress(0)
                .build();
    }

    @Test
    void testCreateReview_Success() {

        PerformanceReviewRequest request = new PerformanceReviewRequest();
        request.setKeyDeliverables("Completed tasks");
        request.setAccomplishments("Achieved goals");
        request.setSelfAssessmentRating(4);

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(i -> i.getArgument(0));

        PerformanceReview result = performanceService.createReview("employee@test.com", request);

        assertNotNull(result);
        assertEquals(ReviewStatus.DRAFT, result.getStatus());
        verify(reviewRepository, times(1)).save(any(PerformanceReview.class));
        verify(notificationService, never()).notifyReviewSubmitted(any(), anyInt());
    }

    @Test
    void testSubmitReview_Success() {

        review.setStatus(ReviewStatus.DRAFT);
        review.setKeyDeliverables("Completed project tasks");
        review.setSelfAssessmentRating(4);
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(reviewRepository.findById(1)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(i -> i.getArgument(0));

        PerformanceReview result = performanceService.submitReview("employee@test.com", 1);

        assertNotNull(result);
        assertEquals(ReviewStatus.SUBMITTED, result.getStatus());
        verify(reviewRepository, times(1)).save(review);
        verify(notificationService, times(1)).notifyReviewSubmitted(employee, 1);
    }

    @Test
    void testCreateGoal_Success() {

        GoalRequest request = new GoalRequest();
        request.setTitle("Learn Spring Boot");
        request.setDescription("Complete Spring Boot course");
        request.setDeadline(LocalDate.now().plusMonths(3));
        request.setPriority("HIGH");

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

        Goal result = performanceService.createGoal("employee@test.com", request);

        assertNotNull(result);
        assertEquals(GoalStatus.NOT_STARTED, result.getStatus());
        assertEquals(GoalPriority.HIGH, result.getPriority());
        verify(goalRepository, times(1)).save(any(Goal.class));
    }

    @Test
    void testUpdateGoalProgress_Success() {

        GoalProgressRequest request = new GoalProgressRequest();
        request.setProgress(50);
        request.setStatus("IN_PROGRESS");

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(goalRepository.findById(1)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

        Goal result = performanceService.updateGoalProgress("employee@test.com", 1, request);

        assertNotNull(result);
        assertEquals(50, result.getProgress());
        assertEquals(GoalStatus.IN_PROGRESS, result.getStatus());
        verify(goalRepository, times(1)).save(goal);
    }

    @Test
    void testUpdateGoalProgress_AlreadyCompleted() {

        goal.setStatus(GoalStatus.COMPLETED);
        GoalProgressRequest request = new GoalProgressRequest();
        request.setProgress(100);

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(goalRepository.findById(1)).thenReturn(Optional.of(goal));

        assertThrows(InvalidActionException.class, () -> {
            performanceService.updateGoalProgress("employee@test.com", 1, request);
        });
    }

    @Test
    void testGetTeamReviews_Success() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<PerformanceReview> page = new PageImpl<>(Collections.singletonList(review));
        when(employeeRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));
        when(reviewRepository.findByManagerCodeAndStatusNot(manager.getEmployeeCode(), ReviewStatus.DRAFT, pageable)).thenReturn(page);

        Page<PerformanceReview> result = performanceService.getTeamReviews("manager@test.com", null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }
}
