package org.example.workforce.service;

import org.example.workforce.dto.*;
import org.example.workforce.exception.*;
import org.example.workforce.model.Employee;
import org.example.workforce.model.Goal;
import org.example.workforce.model.PerformanceReview;
import org.example.workforce.model.enums.GoalPriority;
import org.example.workforce.model.enums.GoalStatus;
import org.example.workforce.model.enums.ReviewStatus;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.repository.GoalRepository;
import org.example.workforce.repository.PerformanceReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PerformanceService {

    @Autowired
    private PerformanceReviewRepository reviewRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private NotificationService notificationService;

    @Transactional
    public PerformanceReview createReview(String email, PerformanceReviewRequest request) {
        Employee employee = getEmployeeByEmail(email);
        reviewRepository.findByEmployee_EmployeeIdAndReviewPeriod(employee.getEmployeeId(), request.getReviewPeriod())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A performance review already exists for period: " + request.getReviewPeriod());
                });
        PerformanceReview review = PerformanceReview.builder()
                .employee(employee).reviewPeriod(request.getReviewPeriod())
                .keyDeliverables(request.getKeyDeliverables())
                .accomplishments(request.getAccomplishments())
                .areasOfImprovement(request.getAreasOfImprovement())
                .selfAssessmentRating(request.getSelfAssessmentRating())
                .status(ReviewStatus.DRAFT).build();
        return reviewRepository.save(review);
    }

    @Transactional
    public PerformanceReview updateReview(String email, Integer reviewId, PerformanceReviewRequest request) {
        Employee employee = getEmployeeByEmail(email);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (!review.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new AccessDeniedException("You can only update your own performance reviews");
        }
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new InvalidActionException("Only draft reviews can be updated. Current status: " + review.getStatus());
        }
        review.setKeyDeliverables(request.getKeyDeliverables());
        review.setAccomplishments(request.getAccomplishments());
        review.setAreasOfImprovement(request.getAreasOfImprovement());
        review.setSelfAssessmentRating(request.getSelfAssessmentRating());
        return reviewRepository.save(review);
    }

    @Transactional
    public PerformanceReview submitReview(String email, Integer reviewId) {
        Employee employee = getEmployeeByEmail(email);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (!review.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new AccessDeniedException("You can only submit your own performance reviews");
        }
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new InvalidActionException("Only draft reviews can be submitted. Current status: " + review.getStatus());
        }
        if (review.getKeyDeliverables() == null || review.getKeyDeliverables().isBlank()) {
            throw new BadRequestException("Key deliverables are required before submitting");
        }
        if (review.getSelfAssessmentRating() == null) {
            throw new BadRequestException("Self assessment rating is required before submitting");
        }
        review.setStatus(ReviewStatus.SUBMITTED);
        review.setSubmittedDate(LocalDateTime.now());
        PerformanceReview savedReview = reviewRepository.save(review);
        notificationService.notifyReviewSubmitted(employee, savedReview.getReviewId());
        return savedReview;
    }

    public Page<PerformanceReview> getMyReviews(String email, ReviewStatus status, Pageable pageable) {
        Employee employee = getEmployeeByEmail(email);
        if (status != null) {
            return reviewRepository.findByEmployee_EmployeeIdAndStatus(employee.getEmployeeId(), status, pageable);
        }
        return reviewRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
    }

    public PerformanceReview getReviewById(String email, Integer reviewId) {
        Employee employee = getEmployeeByEmail(email);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (!review.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new AccessDeniedException("You can only view your own performance reviews");
        }
        return review;
    }

    @Transactional
    public Goal createGoal(String email, GoalRequest request) {
        Employee employee = getEmployeeByEmail(email);
        GoalPriority priority;
        try {
            priority = GoalPriority.valueOf(request.getPriority().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid priority: " + request.getPriority() + ". Use HIGH, MEDIUM, or LOW");
        }
        Goal goal = Goal.builder()
                .employee(employee).title(request.getTitle())
                .description(request.getDescription())
                .year(request.getDeadline().getYear())
                .deadline(request.getDeadline())
                .priority(priority).status(GoalStatus.NOT_STARTED).progress(0).build();
        return goalRepository.save(goal);
    }

    public Page<Goal> getMyGoals(String email, Integer year, GoalStatus status, Pageable pageable) {
        Employee employee = getEmployeeByEmail(email);
        if (status != null) {
            return goalRepository.findByEmployee_EmployeeIdAndStatus(employee.getEmployeeId(), status, pageable);
        }
        if (year != null) {
            return goalRepository.findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), year, pageable);
        }
        return goalRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
    }

    @Transactional
    public Goal updateGoalProgress(String email, Integer goalId, GoalProgressRequest request) {
        Employee employee = getEmployeeByEmail(email);
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found with id: " + goalId));
        if (!goal.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new AccessDeniedException("You can only update your own goals");
        }
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            throw new InvalidActionException("This goal is already marked as completed");
        }
        goal.setProgress(request.getProgress());
        if (request.getProgress() == 100) {
            goal.setStatus(GoalStatus.COMPLETED);
        } else if (request.getProgress() > 0) {
            goal.setStatus(GoalStatus.IN_PROGRESS);
        } else {
            goal.setStatus(GoalStatus.NOT_STARTED);
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                goal.setStatus(GoalStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + request.getStatus() + ". Use NOT_STARTED, IN_PROGRESS, or COMPLETED");
            }
        }
        return goalRepository.save(goal);
    }

    public Page<PerformanceReview> getTeamReviews(String managerEmail, ReviewStatus status, Pageable pageable) {
        Employee manager = getEmployeeByEmail(managerEmail);
        if (status != null) {
            return reviewRepository.findByManagerCodeAndStatus(manager.getEmployeeCode(), status, pageable);
        }

        return reviewRepository.findByManagerCodeAndStatusNot(manager.getEmployeeCode(), ReviewStatus.DRAFT, pageable);
    }

    public PerformanceReview getTeamReviewById(String managerEmail, Integer reviewId) {
        Employee manager = getEmployeeByEmail(managerEmail);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (review.getEmployee().getManager() == null ||
                !review.getEmployee().getManager().getEmployeeCode().equals(manager.getEmployeeCode())) {
            throw new AccessDeniedException("This review does not belong to your team member");
        }
        return review;
    }

    @Transactional
    public PerformanceReview provideReviewFeedback(String managerEmail, Integer reviewId, ManagerFeedbackRequest request) {
        Employee manager = getEmployeeByEmail(managerEmail);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (review.getEmployee().getManager() == null ||
                !review.getEmployee().getManager().getEmployeeCode().equals(manager.getEmployeeCode())) {
            throw new AccessDeniedException("This review does not belong to your team member");
        }
        if (review.getStatus() != ReviewStatus.SUBMITTED) {
            throw new InvalidActionException("Only submitted reviews can be reviewed. Current status: " + review.getStatus());
        }
        review.setReviewer(manager);
        review.setManagerRating(request.getManagerRating());
        review.setManagerFeedback(request.getManagerFeedback());
        review.setStatus(ReviewStatus.REVIEWED);
        review.setReviewedDate(LocalDateTime.now());
        PerformanceReview savedReview = reviewRepository.save(review);
        notificationService.notifyReviewFeedback(review.getEmployee(), savedReview.getReviewId());
        return savedReview;
    }

    public Page<Goal> getTeamMemberGoals(String managerEmail, String employeeCode, Pageable pageable) {
        Employee manager = getEmployeeByEmail(managerEmail);
        return goalRepository.findByEmployeeCodeAndManagerCode(employeeCode, manager.getEmployeeCode(), pageable);
    }

    public Page<Goal> getAllTeamGoals(String managerEmail, Pageable pageable) {
        Employee manager = getEmployeeByEmail(managerEmail);
        return goalRepository.findByManagerCode(manager.getEmployeeCode(), pageable);
    }

    @Transactional
    public Goal commentOnGoal(String managerEmail, Integer goalId, ManagerGoalCommentRequest request) {
        Employee manager = getEmployeeByEmail(managerEmail);
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found with id: " + goalId));
        if (goal.getEmployee().getManager() == null ||
                !goal.getEmployee().getManager().getEmployeeCode().equals(manager.getEmployeeCode())) {
            throw new AccessDeniedException("This goal does not belong to your team member");
        }
        goal.setManagerComments(request.getManagerComments());
        Goal savedGoal = goalRepository.save(goal);
        notificationService.notifyGoalComment(goal.getEmployee(), savedGoal.getGoalId());
        return savedGoal;
    }

    public Page<PerformanceReview> getAllReviews(ReviewStatus status, Pageable pageable) {
        if (status != null) {
            return reviewRepository.findByStatus(status, pageable);
        }

        return reviewRepository.findByStatusNot(ReviewStatus.DRAFT, pageable);
    }

    public PerformanceReview getAdminReviewById(Integer reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
    }

    @Transactional
    public PerformanceReview provideAdminReviewFeedback(String adminEmail, Integer reviewId, ManagerFeedbackRequest request) {
        Employee admin = getEmployeeByEmail(adminEmail);
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found with id: " + reviewId));
        if (review.getStatus() != ReviewStatus.SUBMITTED) {
            throw new InvalidActionException("Only submitted reviews can be reviewed. Current status: " + review.getStatus());
        }
        review.setReviewer(admin);
        review.setManagerRating(request.getManagerRating());
        review.setManagerFeedback(request.getManagerFeedback());
        review.setStatus(ReviewStatus.REVIEWED);
        review.setReviewedDate(LocalDateTime.now());
        PerformanceReview savedReview = reviewRepository.save(review);
        notificationService.notifyReviewFeedback(review.getEmployee(), savedReview.getReviewId());
        return savedReview;
    }

    private Employee getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
    }
}
