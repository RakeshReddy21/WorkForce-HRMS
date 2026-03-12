package org.example.workforce.service;

import org.example.workforce.dto.LeaveAnalysisResponse;
import org.example.workforce.integration.OllamaClient;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.repository.LeaveApplicationRepository;
import org.example.workforce.repository.LeaveBalanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered leave analysis for managers/admins reviewing leave requests.
 * Provides historical patterns, team impact analysis, and AI recommendations.
 */
@Service
public class LeaveAnalysisService {

    @Autowired private LeaveApplicationRepository leaveApplicationRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private OllamaClient ollamaClient;

    public LeaveAnalysisResponse analyzeLeaveRequest(Integer leaveId) {
        LeaveApplication request = leaveApplicationRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave application not found: " + leaveId));

        Employee employee = request.getEmployee();
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        // ─── Fetch all leave history ───
        List<LeaveApplication> allLeaves = leaveApplicationRepository
                .findByEmployeeEmployeeId(employee.getEmployeeId().intValue());

        List<LeaveApplication> thisYearLeaves = allLeaves.stream()
                .filter(l -> l.getStartDate().getYear() == currentYear)
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.PENDING)
                .toList();

        List<LeaveApplication> lastYearLeaves = allLeaves.stream()
                .filter(l -> l.getStartDate().getYear() == currentYear - 1)
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .toList();

        // ─── Leave stats by type ───
        Map<String, Integer> leavesByType = thisYearLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .collect(Collectors.groupingBy(
                        l -> l.getLeaveType().getLeaveTypeName(),
                        Collectors.summingInt(LeaveApplication::getTotalDays)));

        // ─── Leave stats by month ───
        Map<String, Integer> leavesByMonth = thisYearLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .collect(Collectors.groupingBy(
                        l -> l.getStartDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        Collectors.summingInt(LeaveApplication::getTotalDays)));

        // ─── Average leave duration ───
        double avgDuration = thisYearLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .mapToInt(LeaveApplication::getTotalDays)
                .average().orElse(0);

        // ─── Current balances ───
        List<LeaveBalance> balances = leaveBalanceRepository
                .findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear);
        Map<String, Integer> currentBalances = balances.stream()
                .collect(Collectors.toMap(
                        b -> b.getLeaveType().getLeaveTypeName(),
                        LeaveBalance::getAvailableBalance));

        // ─── Balance after approval ───
        String requestedType = request.getLeaveType().getLeaveTypeName();
        int currentBalance = currentBalances.getOrDefault(requestedType, 0);
        int balanceAfterApproval = currentBalance - request.getTotalDays();

        // ─── Pattern detection ───
        List<String> patterns = detectPatterns(thisYearLeaves);

        // ─── Frequency trend ───
        int thisYearCount = thisYearLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .mapToInt(LeaveApplication::getTotalDays).sum();
        int lastYearCount = lastYearLeaves.stream()
                .mapToInt(LeaveApplication::getTotalDays).sum();
        String trend = thisYearCount > lastYearCount * 1.2 ? "INCREASING" :
                thisYearCount < lastYearCount * 0.8 ? "DECREASING" : "STABLE";

        // ─── Team members on leave today ───
        int teamOnLeave = 0;
        if (employee.getManager() != null) {
            teamOnLeave = countTeamMembersOnLeave(employee.getManager().getEmployeeId(), today);
        }

        // ─── Pending requests count ───
        long pendingCount = allLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.PENDING)
                .count();

        // ─── Build response ───
        LeaveAnalysisResponse response = LeaveAnalysisResponse.builder()
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .department(employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : "N/A")
                .designation(employee.getDesignation() != null ? employee.getDesignation().getDesignationName() : "N/A")
                .totalLeavesTakenThisYear(thisYearCount)
                .totalLeavesTakenLastYear(lastYearCount)
                .leavesByType(leavesByType)
                .leavesByMonth(leavesByMonth)
                .pendingLeaveRequests((int) pendingCount)
                .averageLeaveDuration(Math.round(avgDuration * 10.0) / 10.0)
                .currentBalances(currentBalances)
                .patterns(patterns)
                .frequencyTrend(trend)
                .teamMembersOnLeaveToday(teamOnLeave)
                .requestedDays(request.getTotalDays())
                .requestedType(requestedType)
                .balanceAfterApproval(balanceAfterApproval)
                .build();

        // ─── AI Analysis ───
        generateAiAnalysis(response, request);

        return response;
    }

    private List<String> detectPatterns(List<LeaveApplication> leaves) {
        List<String> patterns = new ArrayList<>();
        List<LeaveApplication> approved = leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED).toList();

        if (approved.isEmpty()) {
            patterns.add("No approved leave history this year");
            return patterns;
        }

        // Monday/Friday pattern
        long mondayFriday = approved.stream()
                .filter(l -> {
                    DayOfWeek dow = l.getStartDate().getDayOfWeek();
                    return dow == DayOfWeek.MONDAY || dow == DayOfWeek.FRIDAY;
                }).count();
        if (mondayFriday > approved.size() * 0.5 && approved.size() >= 3) {
            patterns.add("Frequently takes leave on Mondays/Fridays (" + mondayFriday + "/" + approved.size() + " requests)");
        }

        // Consecutive short leaves
        long singleDays = approved.stream().filter(l -> l.getTotalDays() == 1).count();
        if (singleDays > 3) {
            patterns.add("Takes many single-day leaves (" + singleDays + " this year)");
        }

        // Monthly clustering
        Map<Integer, Long> byMonth = approved.stream()
                .collect(Collectors.groupingBy(l -> l.getStartDate().getMonthValue(), Collectors.counting()));
        byMonth.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .forEach(e -> patterns.add("High leave frequency in " +
                        java.time.Month.of(e.getKey()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)));

        if (patterns.isEmpty()) {
            patterns.add("No unusual patterns detected");
        }

        return patterns;
    }

    private int countTeamMembersOnLeave(Integer managerId, LocalDate date) {
        List<LeaveApplication> teamLeaves = leaveApplicationRepository.findAll().stream()
                .filter(l -> l.getEmployee().getManager() != null
                        && l.getEmployee().getManager().getEmployeeId().equals(managerId))
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()))
                .toList();
        return teamLeaves.size();
    }

    private void generateAiAnalysis(LeaveAnalysisResponse response, LeaveApplication request) {
        try {
            String prompt = String.format("""
                    You are an HR analytics assistant. Analyze this leave request and provide a recommendation.
                    Keep your response concise (3-5 sentences).
                    
                    Employee: %s | Department: %s | Designation: %s
                    Request: %d days of %s (from %s to %s)
                    Reason: %s
                    
                    Leave History This Year: %d days taken | Last Year: %d days
                    Current Balance for %s: %d days (after approval: %d)
                    Frequency Trend: %s
                    Team Members on Leave Today: %d
                    Patterns: %s
                    
                    Provide:
                    1. SUMMARY: Brief analysis (2-3 sentences)
                    2. RECOMMENDATION: APPROVE or REVIEW_FURTHER
                    3. REASONS: Key reasons for your recommendation (comma-separated)
                    
                    Format your response exactly as:
                    SUMMARY: [your analysis]
                    RECOMMENDATION: [APPROVE or REVIEW_FURTHER]
                    REASONS: [reason1, reason2, reason3]
                    """,
                    response.getEmployeeName(), response.getDepartment(), response.getDesignation(),
                    request.getTotalDays(), request.getLeaveType().getLeaveTypeName(),
                    request.getStartDate(), request.getEndDate(),
                    request.getReason() != null ? request.getReason() : "Not specified",
                    response.getTotalLeavesTakenThisYear(), response.getTotalLeavesTakenLastYear(),
                    response.getRequestedType(), response.getBalanceAfterApproval() + request.getTotalDays(),
                    response.getBalanceAfterApproval(),
                    response.getFrequencyTrend(), response.getTeamMembersOnLeaveToday(),
                    String.join("; ", response.getPatterns()));

            String aiResponse = ollamaClient.generate(prompt);

            if (aiResponse != null && !aiResponse.startsWith("Error")) {
                // Parse structured response
                String summary = extractField(aiResponse, "SUMMARY:");
                String recommendation = extractField(aiResponse, "RECOMMENDATION:");
                String reasons = extractField(aiResponse, "REASONS:");

                response.setAiSummary(summary != null ? summary : aiResponse.trim());
                response.setAiRecommendation(recommendation != null && recommendation.contains("REVIEW") ?
                        "REVIEW_FURTHER" : "APPROVE");
                response.setAiReasons(reasons != null ?
                        Arrays.asList(reasons.split(",\\s*")) :
                        List.of("Based on available data"));
            } else {
                setDefaultAnalysis(response);
            }
        } catch (Exception e) {
            setDefaultAnalysis(response);
        }
    }

    private void setDefaultAnalysis(LeaveAnalysisResponse response) {
        response.setAiSummary("AI analysis unavailable. Review based on the data provided above.");
        response.setAiRecommendation(response.getBalanceAfterApproval() >= 0 ? "APPROVE" : "REVIEW_FURTHER");
        response.setAiReasons(List.of("Manual review recommended"));
    }

    private String extractField(String text, String fieldName) {
        int idx = text.indexOf(fieldName);
        if (idx < 0) return null;
        String after = text.substring(idx + fieldName.length()).trim();
        int newline = after.indexOf('\n');
        return newline >= 0 ? after.substring(0, newline).trim() : after.trim();
    }
}

