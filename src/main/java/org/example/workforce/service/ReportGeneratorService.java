package org.example.workforce.service;

import org.example.workforce.dto.PerformanceReportResponse;
import org.example.workforce.integration.OllamaClient;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.GoalStatus;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ReportGeneratorService.class);

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
        // Check Ollama availability first to avoid long timeouts
        if (!ollamaClient.isAvailable()) {
            log.info("Ollama not available — using data-driven assessment fallback");
            setDefaultAssessment(response);
            return;
        }

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

            String aiResponse = ollamaClient.generate(prompt, 300);

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
        // Generate meaningful data-driven assessment even without Ollama
        StringBuilder overall = new StringBuilder();
        StringBuilder strengths = new StringBuilder();
        StringBuilder improvements = new StringBuilder();
        StringBuilder recommendations = new StringBuilder();

        // ─── Overall Assessment ───
        overall.append(response.getEmployeeName()).append(" has been present for ")
                .append(response.getTotalPresentDays()).append(" days with an average of ")
                .append(response.getAverageHoursPerDay()).append(" hours per day. ");

        double goalRate = response.getTotalGoals() > 0
                ? (double) response.getCompletedGoals() / response.getTotalGoals() * 100 : 0;
        if (goalRate >= 80) {
            overall.append("Goal completion rate is excellent at ").append(String.format("%.0f", goalRate)).append("%. ");
        } else if (goalRate >= 50) {
            overall.append("Goal completion is moderate at ").append(String.format("%.0f", goalRate)).append("%. ");
        } else if (response.getTotalGoals() > 0) {
            overall.append("Goal completion needs attention at ").append(String.format("%.0f", goalRate)).append("%. ");
        }

        if (response.getAverageManagerRating() >= 4) {
            overall.append("Manager ratings indicate strong performance.");
        } else if (response.getAverageManagerRating() >= 3) {
            overall.append("Manager ratings indicate solid, consistent performance.");
        } else if (response.getAverageManagerRating() > 0) {
            overall.append("Manager ratings suggest room for improvement.");
        }

        // ─── Strengths ───
        if (response.getTotalPresentDays() > 0 && response.getLateArrivals() <= 2) {
            strengths.append("Demonstrates strong punctuality and attendance discipline. ");
        }
        if (response.getAverageHoursPerDay() >= 8) {
            strengths.append("Consistently puts in full working hours (avg ").append(response.getAverageHoursPerDay()).append("h/day). ");
        }
        if (goalRate >= 70) {
            strengths.append("Shows strong goal execution with ").append(response.getCompletedGoals())
                    .append(" of ").append(response.getTotalGoals()).append(" goals completed. ");
        }
        if (response.getAverageSelfRating() > 0 && response.getAverageManagerRating() > 0
                && Math.abs(response.getAverageSelfRating() - response.getAverageManagerRating()) <= 1) {
            strengths.append("Self-assessment aligns well with manager evaluation, indicating good self-awareness.");
        }
        if (strengths.isEmpty()) {
            strengths.append("Employee shows consistency in their work routine and availability.");
        }

        // ─── Areas for Improvement ───
        if (response.getLateArrivals() > 3) {
            improvements.append("Late arrivals (").append(response.getLateArrivals())
                    .append(" instances) should be reduced. ");
        }
        if (response.getTotalAbsentDays() > 5) {
            improvements.append("High absence count (").append(response.getTotalAbsentDays())
                    .append(" days) — consider discussing attendance expectations. ");
        }
        if (goalRate < 50 && response.getTotalGoals() > 0) {
            improvements.append("Goal completion rate of ").append(String.format("%.0f", goalRate))
                    .append("% is below expectations — needs focused effort on pending goals. ");
        }
        if (response.getAverageHoursPerDay() < 7 && response.getAverageHoursPerDay() > 0) {
            improvements.append("Average working hours (").append(response.getAverageHoursPerDay())
                    .append("h/day) are below the standard 8-hour benchmark. ");
        }
        if (improvements.isEmpty()) {
            improvements.append("No major concerns identified. Continue maintaining current performance standards and explore leadership opportunities.");
        }

        // ─── Recommendations ───
        if (response.getInProgressGoals() > 0) {
            recommendations.append("Focus on completing the ").append(response.getInProgressGoals())
                    .append(" in-progress goal(s) before their deadlines. ");
        }
        if (response.getAverageManagerRating() == 0) {
            recommendations.append("Schedule a manager review session to get formal performance feedback. ");
        }
        if (response.getLateArrivals() > 3) {
            recommendations.append("Discuss time management strategies to improve punctuality. ");
        }
        recommendations.append("Schedule regular 1-on-1 check-ins to align on priorities and career growth.");

        // ─── Rating ───
        String rating;
        double score = 0;
        if (response.getAverageManagerRating() > 0) score += response.getAverageManagerRating() * 0.4;
        else score += 3 * 0.4; // default 3
        if (response.getTotalGoals() > 0) score += (goalRate / 100.0) * 5 * 0.3;
        else score += 3 * 0.3;
        if (response.getTotalPresentDays() > 0) {
            double attendScore = Math.min(5, 5.0 * response.getTotalPresentDays() / Math.max(response.getTotalPresentDays() + response.getTotalAbsentDays(), 1));
            score += attendScore * 0.3;
        } else {
            score += 3 * 0.3;
        }

        if (score >= 4.5) rating = "EXCEPTIONAL";
        else if (score >= 3.5) rating = "EXCEEDS_EXPECTATIONS";
        else if (score >= 2.5) rating = "MEETS_EXPECTATIONS";
        else rating = "NEEDS_IMPROVEMENT";

        response.setAiOverallAssessment(overall.toString());
        response.setAiStrengths(strengths.toString());
        response.setAiAreasForImprovement(improvements.toString());
        response.setAiRecommendations(recommendations.toString());
        response.setAiRating(rating);
    }

    private String extractField(String text, String fieldName) {
        int idx = text.indexOf(fieldName);
        if (idx < 0) return null;
        String after = text.substring(idx + fieldName.length()).trim();
        int newline = after.indexOf('\n');
        return newline >= 0 ? after.substring(0, newline).trim() : after.trim();
    }
}

