package org.example.workforce.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceReportResponse {
    // Employee info
    private String employeeName;
    private String employeeCode;
    private String department;
    private String designation;
    private String reportPeriod;

    // Attendance summary
    private int totalPresentDays;
    private int totalAbsentDays;
    private int lateArrivals;
    private double averageHoursPerDay;

    // Leave summary
    private int totalLeavesTaken;
    private Map<String, Integer> leaveBreakdown;

    // Goals summary
    private int totalGoals;
    private int completedGoals;
    private int inProgressGoals;
    private double averageGoalProgress;
    private List<GoalSummary> goals;

    // Performance reviews
    private List<ReviewSummary> reviews;
    private Double averageSelfRating;
    private Double averageManagerRating;

    // AI-generated report
    private String aiOverallAssessment;
    private String aiStrengths;
    private String aiAreasForImprovement;
    private String aiRecommendations;
    private String aiRating;                    // "EXCEPTIONAL", "EXCEEDS_EXPECTATIONS", "MEETS_EXPECTATIONS", "NEEDS_IMPROVEMENT"

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GoalSummary {
        private String title;
        private String status;
        private int progress;
        private String priority;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReviewSummary {
        private String period;
        private Integer selfRating;
        private Integer managerRating;
        private String status;
    }
}

