package org.example.workforce.service;

import org.example.workforce.dto.PerformanceReportResponse;
import org.example.workforce.integration.OllamaClient;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.GoalStatus;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered performance report generator.
 * Aggregates goals, reviews, attendance, and leave data to produce
 * comprehensive performance assessments with AI-generated insights.
 */
@Service
public class ReportGeneratorService {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceService attendanceService;
    @Autowired private LeaveApplicationRepository leaveApplicationRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private PerformanceReviewRepository performanceReviewRepository;
    @Autowired private OllamaClient ollamaClient;

    /**
     * Generate a comprehensive AI-powered performance report for an employee.
     *
     * @param employeeId The employee to generate the report for
     * @param period     Optional period string (e.g., "Q1 2026", "2025-2026")
     */
    public PerformanceReportResponse generateReport(Integer employeeId, String period) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
        int currentYear = LocalDate.now().getYear();
        String reportPeriod = period != null ? period : "FY " + currentYear;

        // ─── Goals ───
        List<Goal> goals = goalRepository.findByEmployeeEmployeeIdAndYear(employee.getEmployeeId(), currentYear);

        int completedGoals = (int) goals.stream().filter(g -> g.getStatus() == GoalStatus.COMPLETED).count();
        int inProgressGoals = (int) goals.stream().filter(g -> g.getStatus() == GoalStatus.IN_PROGRESS).count();
        double avgProgress = goals.stream().mapToInt(Goal::getProgress).average().orElse(0);

        List<PerformanceReportResponse.GoalSummary> goalSummaries = goals.stream()
                .map(g -> PerformanceReportResponse.GoalSummary.builder()
                        .title(g.getTitle())
                        .status(g.getStatus().name())
                        .progress(g.getProgress())
                        .priority(g.getPriority().name())
                        .build())
                .toList();

        // ─── Performance Reviews ───
        List<PerformanceReview> reviews = performanceReviewRepository
                .findByEmployeeEmployeeId(employee.getEmployeeId());

        List<PerformanceReportResponse.ReviewSummary> reviewSummaries = reviews.stream()
                .map(r -> PerformanceReportResponse.ReviewSummary.builder()
                        .period(r.getReviewPeriod())
                        .selfRating(r.getSelfAssessmentRating())
                        .managerRating(r.getManagerRating())
                        .status(r.getStatus().name())
                        .build())
                .toList();

        Double avgSelfRating = reviews.stream()
                .filter(r -> r.getSelfAssessmentRating() != null)
                .mapToInt(PerformanceReview::getSelfAssessmentRating)
                .average().stream().findFirst().orElse(0);

        Double avgManagerRating = reviews.stream()
                .filter(r -> r.getManagerRating() != null)
                .mapToInt(PerformanceReview::getManagerRating)
                .average().stream().findFirst().orElse(0);

        // ─── Attendance ───
        var summary = attendanceService.getMySummary(
                employee.getEmail(), null, null);

        // ─── Leaves ───
        List<LeaveApplication> leaves = leaveApplicationRepository
                .findByEmployeeEmployeeId(employee.getEmployeeId()).stream()
                .filter(l -> l.getStartDate().getYear() == currentYear)
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .toList();

        int totalLeaves = leaves.stream().mapToInt(LeaveApplication::getTotalDays).sum();
        Map<String, Integer> leaveBreakdown = leaves.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getLeaveType().getLeaveTypeName(),
                        Collectors.summingInt(LeaveApplication::getTotalDays)));

        // ─── Build Response ───
        PerformanceReportResponse response = PerformanceReportResponse.builder()
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .employeeCode(employee.getEmployeeCode())
                .department(employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : "N/A")
                .designation(employee.getDesignation() != null ? employee.getDesignation().getDesignationName() : "N/A")
                .reportPeriod(reportPeriod)
                .totalPresentDays((int) summary.getTotalPresent())
                .totalAbsentDays((int) summary.getTotalAbsent())
                .lateArrivals((int) summary.getTotalLateArrivals())
                .averageHoursPerDay(summary.getTotalHoursWorked() != null ?
                        Math.round(summary.getTotalHoursWorked() / Math.max(summary.getTotalPresent(), 1) * 10.0) / 10.0 : 0)
                .totalLeavesTaken(totalLeaves)
                .leaveBreakdown(leaveBreakdown)
                .totalGoals(goals.size())
                .completedGoals(completedGoals)
                .inProgressGoals(inProgressGoals)
                .averageGoalProgress(Math.round(avgProgress * 10.0) / 10.0)
                .goals(goalSummaries)
                .reviews(reviewSummaries)
                .averageSelfRating(avgSelfRating)
                .averageManagerRating(avgManagerRating)
                .build();

        // ─── AI-Generated Assessment ───
        generateAiAssessment(response, employee, reviews);

        return response;
    }

    private void generateAiAssessment(PerformanceReportResponse response, Employee employee,
                                       List<PerformanceReview> reviews) {
        try {
            StringBuilder reviewDetails = new StringBuilder();
            for (PerformanceReview r : reviews) {
                reviewDetails.append(String.format("Period: %s | Self: %s | Manager: %s | Accomplishments: %s | Areas to improve: %s\n",
                        r.getReviewPeriod(),
                        r.getSelfAssessmentRating() != null ? r.getSelfAssessmentRating() : "N/A",
                        r.getManagerRating() != null ? r.getManagerRating() : "N/A",
                        r.getAccomplishments() != null ? r.getAccomplishments() : "N/A",
                        r.getAreasOfImprovement() != null ? r.getAreasOfImprovement() : "N/A"));
            }

            StringBuilder goalDetails = new StringBuilder();
            for (var g : response.getGoals()) {
                goalDetails.append(String.format("Goal: %s | Status: %s | Progress: %d%% | Priority: %s\n",
                        g.getTitle(), g.getStatus(), g.getProgress(), g.getPriority()));
            }

            String prompt = String.format("""
                    You are a senior HR analyst. Generate a professional performance assessment.
                    Keep each section to 2-4 sentences. Be specific and constructive.
                    
                    Employee: %s (%s) | Department: %s | Designation: %s
                    
                    ATTENDANCE: Present %d days | Absent %d days | Late %d times | Avg hours/day: %.1f
                    LEAVES: %d days taken (%s)
                    
                    GOALS (%d total, %d completed, avg progress %s%%):
                    %s
                    
                    PERFORMANCE REVIEWS:
                    %s
                    Avg Self Rating: %.1f | Avg Manager Rating: %.1f
                    
                    Respond EXACTLY in this format:
                    OVERALL_ASSESSMENT: [2-3 sentence overall performance summary]
                    STRENGTHS: [Key strengths, 2-3 sentences]
                    AREAS_FOR_IMPROVEMENT: [Areas needing improvement, 2-3 sentences]
                    RECOMMENDATIONS: [Actionable recommendations, 2-3 sentences]
                    RATING: [One of: EXCEPTIONAL, EXCEEDS_EXPECTATIONS, MEETS_EXPECTATIONS, NEEDS_IMPROVEMENT]
                    """,
                    response.getEmployeeName(), response.getEmployeeCode(),
                    response.getDepartment(), response.getDesignation(),
                    response.getTotalPresentDays(), response.getTotalAbsentDays(),
                    response.getLateArrivals(), response.getAverageHoursPerDay(),
                    response.getTotalLeavesTaken(), response.getLeaveBreakdown().toString(),
                    response.getTotalGoals(), response.getCompletedGoals(), response.getAverageGoalProgress(),
                    goalDetails, reviewDetails,
                    response.getAverageSelfRating(), response.getAverageManagerRating());

            String aiResponse = ollamaClient.generate(prompt);

            if (aiResponse != null && !aiResponse.startsWith("Error")) {
                response.setAiOverallAssessment(extractField(aiResponse, "OVERALL_ASSESSMENT:"));
                response.setAiStrengths(extractField(aiResponse, "STRENGTHS:"));
                response.setAiAreasForImprovement(extractField(aiResponse, "AREAS_FOR_IMPROVEMENT:"));
                response.setAiRecommendations(extractField(aiResponse, "RECOMMENDATIONS:"));

                String rating = extractField(aiResponse, "RATING:");
                response.setAiRating(rating != null ? rating.trim().toUpperCase().replace(" ", "_") : "MEETS_EXPECTATIONS");
            } else {
                setDefaultAssessment(response);
            }
        } catch (Exception e) {
            setDefaultAssessment(response);
        }
    }

    private void setDefaultAssessment(PerformanceReportResponse response) {
        response.setAiOverallAssessment("AI assessment unavailable. Please review the data manually.");
        response.setAiStrengths("Data-driven assessment pending AI availability.");
        response.setAiAreasForImprovement("Data-driven assessment pending AI availability.");
        response.setAiRecommendations("Schedule a 1-on-1 review meeting to discuss performance.");
        response.setAiRating("MEETS_EXPECTATIONS");
    }

    private String extractField(String text, String fieldName) {
        int idx = text.indexOf(fieldName);
        if (idx < 0) return null;
        String after = text.substring(idx + fieldName.length()).trim();
        int newline = after.indexOf('\n');
        return newline >= 0 ? after.substring(0, newline).trim() : after.trim();
    }
}

