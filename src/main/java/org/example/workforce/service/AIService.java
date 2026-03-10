package org.example.workforce.service;

import org.example.workforce.dto.*;
import org.example.workforce.integration.OllamaClient;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.model.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AIService {

    // ═══════════════════════════════════════════
    //  DEPENDENCIES
    // ═══════════════════════════════════════════

    @Autowired private OllamaClient ollamaClient;
    @Autowired private LeaveService leaveService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private EmployeeService employeeService;
    @Autowired private DashboardService dashboardService;
    @Autowired private AnnouncementService announcementService;
    @Autowired private PerformanceService performanceService;
    @Autowired private NotificationService notificationService;

    // ═══════════════════════════════════════════
    //  CONVERSATION STATE
    // ═══════════════════════════════════════════

    private final ConcurrentHashMap<String, ConversationContext> activeFlows = new ConcurrentHashMap<>();

    private static class ConversationContext {
        String flow;
        int step;
        Map<String, String> data = new HashMap<>();
        long lastActivity = System.currentTimeMillis();

        ConversationContext(String flow) {
            this.flow = flow;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastActivity > 600_000; // 10 min
        }

        void touch() {
            lastActivity = System.currentTimeMillis();
        }
    }

    // ═══════════════════════════════════════════
    //  MAIN ENTRY POINT
    // ═══════════════════════════════════════════

    public AIChatResponse processMessage(String email, AIChatRequest request) {
        String message = request.getMessage().trim();
        String lower = message.toLowerCase();

        // Get user role
        Role userRole = getUserRole(email);

        // Clean expired contexts
        activeFlows.entrySet().removeIf(e -> e.getValue().isExpired());

        // Check active flow
        ConversationContext ctx = activeFlows.get(email);
        if (ctx != null && !ctx.isExpired()) {
            if (isAbortCommand(lower)) {
                activeFlows.remove(email);
                return reply("No problem, I've cancelled that. What else can I help you with?",
                        getDefaultQuickReplies(userRole));
            }
            ctx.touch();
            return continueFlow(email, ctx, message);
        }

        // Detect intent
        String intent = detectIntent(lower, userRole);
        if (intent != null) {
            return startAction(email, intent, message, userRole);
        }

        // General chat
        return handleGeneralChat(request, userRole);
    }

    private Role getUserRole(String email) {
        try {
            Employee emp = employeeService.getEmployeeByEmail(email);
            return emp.getRole();
        } catch (Exception e) {
            return Role.EMPLOYEE;
        }
    }

    private String[] getDefaultQuickReplies(Role role) {
        return switch (role) {
            case ADMIN -> new String[]{"Admin Dashboard", "All Leaves", "Team Attendance", "My Profile", "Help"};
            case MANAGER -> new String[]{"Team Leaves", "Team Attendance", "Apply Leave", "My Profile", "Help"};
            default -> new String[]{"Apply Leave", "My Leaves", "Check In", "My Profile", "Help"};
        };
    }

    // ═══════════════════════════════════════════
    //  INTENT DETECTION (keyword-based, reliable)
    // ═══════════════════════════════════════════

    private String detectIntent(String msg, Role userRole) {
        // ── Manager / Admin specific intents (check first) ──
        if (userRole == Role.MANAGER || userRole == Role.ADMIN) {
            if (containsAny(msg, "team leaves", "team leave", "team's leave",
                    "pending team", "my team leaves", "team requests")) {
                return "GET_TEAM_LEAVES";
            }
            if (containsAny(msg, "approve leave", "approve the leave", "accept leave")) {
                return "APPROVE_LEAVE";
            }
            if (containsAny(msg, "reject leave", "reject the leave", "decline leave", "deny leave")) {
                return "REJECT_LEAVE";
            }
            if (containsAny(msg, "team attendance", "team's attendance", "my team attendance",
                    "who is present", "team present", "who checked in")) {
                return "GET_TEAM_ATTENDANCE";
            }
            if (containsAny(msg, "team reviews", "team performance", "team review")) {
                return "GET_TEAM_REVIEWS";
            }
        }
        // ── Admin-only intents ──
        if (userRole == Role.ADMIN) {
            if (containsAny(msg, "admin dashboard", "admin stats", "admin overview",
                    "system dashboard", "workforce stats", "overall stats")) {
                return "GET_ADMIN_DASHBOARD";
            }
            if (containsAny(msg, "all leaves", "all leave applications", "all leave requests",
                    "company leaves", "everyone's leave")) {
                return "GET_ALL_LEAVES";
            }
        }
        // Apply leave
        if (containsAny(msg, "apply leave", "apply for leave", "request leave",
                "want leave", "need leave", "take leave", "apply sick",
                "apply casual", "apply earned", "want a leave", "need a leave",
                "take a leave", "apply a leave", "leave for me", "book leave",
                "submit leave", "apply for a leave", "leave apply", "leave request",
                "can you apply", "please apply", "i want to apply")) {
            return "APPLY_LEAVE";
        }
        // Cancel leave
        if (containsAny(msg, "cancel leave", "cancel my leave", "withdraw leave",
                "revoke leave", "delete leave", "cancel a leave")) {
            return "CANCEL_LEAVE";
        }
        // Check in
        if (containsAny(msg, "check in", "checkin", "check-in", "clock in",
                "punch in", "mark attendance", "start work", "mark my attendance")) {
            if (!containsAny(msg, "did i check", "have i check", "am i check",
                    "today check", "my check-in", "check in today", "checked in")) {
                return "CHECK_IN";
            }
        }
        // Check out
        if (containsAny(msg, "check out", "checkout", "check-out", "clock out",
                "punch out", "end work", "done for today", "log off", "sign off")) {
            return "CHECK_OUT";
        }
        // Update profile
        if (containsAny(msg, "update profile", "update my profile", "change my phone",
                "change phone", "update phone", "change address", "update address",
                "update emergency", "change emergency", "edit profile", "edit my profile")) {
            return "UPDATE_PROFILE";
        }
        // Create goal
        if (containsAny(msg, "create goal", "new goal", "add goal", "set goal",
                "create a goal", "add a goal", "set a goal")) {
            return "CREATE_GOAL";
        }
        // Leave balance
        if (containsAny(msg, "leave balance", "how many leaves", "my leaves balance",
                "leave details", "leaves do i have", "remaining leaves",
                "available leaves", "check my leave", "show leave balance",
                "leave count", "leave remaining")) {
            return "GET_LEAVE_BALANCE";
        }
        // My leaves list
        if (containsAny(msg, "my leaves", "my leave applications", "my leave requests",
                "show my leaves", "list my leaves", "pending leaves",
                "my pending leave", "leave history", "leave applications")) {
            return "GET_MY_LEAVES";
        }
        // Attendance today
        if (containsAny(msg, "today attendance", "today's attendance", "did i check",
                "attendance today", "am i checked in", "have i checked in",
                "my check-in today", "today status", "checked in today")) {
            return "GET_ATTENDANCE_TODAY";
        }
        // Attendance summary
        if (containsAny(msg, "attendance summary", "attendance report", "monthly attendance",
                "attendance this month", "my attendance", "attendance record",
                "attendance history", "show attendance", "present days", "absent days")) {
            return "GET_ATTENDANCE_SUMMARY";
        }
        // Dashboard
        if (containsAny(msg, "my dashboard", "dashboard summary", "show dashboard",
                "dashboard details", "my overview", "my summary")) {
            return "GET_MY_DASHBOARD";
        }
        // Announcements
        if (containsAny(msg, "announcement", "announcements", "any announcement",
                "company news", "latest news", "notices", "company update",
                "recent announcements")) {
            return "GET_ANNOUNCEMENTS";
        }
        // Profile
        if (containsAny(msg, "my profile", "profile details", "show profile",
                "my details", "my information", "my info", "about me",
                "employee details", "show my profile", "view profile")) {
            return "GET_MY_PROFILE";
        }
        // Holidays
        if (containsAny(msg, "holiday", "holidays", "upcoming holiday",
                "public holiday", "holiday list", "holiday dates", "next holiday",
                "upcoming holidays", "show holidays")) {
            return "GET_HOLIDAYS";
        }
        // My goals
        if (containsAny(msg, "my goals", "show goals", "show my goals",
                "goal progress", "my targets", "list goals", "view goals")) {
            return "GET_MY_GOALS";
        }
        // My reviews
        if (containsAny(msg, "my reviews", "performance review", "my performance",
                "show reviews", "show my reviews", "review status",
                "my performance reviews")) {
            return "GET_MY_REVIEWS";
        }
        // Notifications
        if (containsAny(msg, "notifications", "my notifications", "unread notifications",
                "notification count", "any notifications", "show notifications")) {
            return "GET_NOTIFICATIONS";
        }
        // Help
        if (containsAny(msg, "help", "what can you do", "capabilities",
                "what do you do", "how to", "commands", "features",
                "what all can you", "guide me")) {
            return "HELP";
        }
        // Greetings
        if (containsAny(msg, "hello", "hi", "hey", "good morning",
                "good afternoon", "good evening")) {
            return "GREETING";
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  START ACTION / DISPATCH
    // ═══════════════════════════════════════════

    private AIChatResponse startAction(String email, String intent, String message, Role userRole) {
        try {
            switch (intent) {
                // Multi-turn flows (all roles)
                case "APPLY_LEAVE":    return startApplyLeaveFlow(email, message);
                case "CANCEL_LEAVE":   return startCancelLeaveFlow(email);
                case "CHECK_IN":       return startCheckInFlow(email);
                case "CHECK_OUT":      return startCheckOutFlow(email);
                case "UPDATE_PROFILE": return startUpdateProfileFlow(email);
                case "CREATE_GOAL":    return startCreateGoalFlow(email, message);

                // Single-turn queries (all roles)
                case "GET_LEAVE_BALANCE":     return getLeaveBalance(email);
                case "GET_MY_LEAVES":         return getMyLeaves(email);
                case "GET_ATTENDANCE_TODAY":   return getAttendanceToday(email);
                case "GET_ATTENDANCE_SUMMARY": return getAttendanceSummary(email);
                case "GET_MY_DASHBOARD":      return getDashboard(email);
                case "GET_ANNOUNCEMENTS":     return getAnnouncements();
                case "GET_MY_PROFILE":        return getProfile(email);
                case "GET_HOLIDAYS":          return getHolidays();
                case "GET_MY_GOALS":          return getMyGoals(email);
                case "GET_MY_REVIEWS":        return getMyReviews(email);
                case "GET_NOTIFICATIONS":     return getNotifications(email);
                case "HELP":                  return getHelp(userRole);
                case "GREETING":              return getGreeting(email, userRole);

                // Manager / Admin actions
                case "GET_TEAM_LEAVES":       return getTeamLeaves(email, userRole);
                case "APPROVE_LEAVE":         return startApproveLeavFlow(email, userRole);
                case "REJECT_LEAVE":          return startRejectLeaveFlow(email, userRole);
                case "GET_TEAM_ATTENDANCE":   return getTeamAttendance(email, userRole);
                case "GET_TEAM_REVIEWS":      return getTeamReviews(email, userRole);

                // Admin-only actions
                case "GET_ADMIN_DASHBOARD":   return getAdminDashboard(email, userRole);
                case "GET_ALL_LEAVES":        return getAllLeaves(email, userRole);

                default:
                    return reply("I'm not sure how to help with that. Type 'help' to see what I can do.",
                            getDefaultQuickReplies(userRole));
            }
        } catch (Exception e) {
            return reply("Sorry, something went wrong: " + e.getMessage(),
                    getDefaultQuickReplies(userRole));
        }
    }

    // ═══════════════════════════════════════════
    //  CONTINUE ACTIVE FLOW
    // ═══════════════════════════════════════════

    private AIChatResponse continueFlow(String email, ConversationContext ctx, String message) {
        try {
            switch (ctx.flow) {
                case "APPLY_LEAVE":    return continueApplyLeave(email, ctx, message);
                case "CANCEL_LEAVE":   return continueCancelLeave(email, ctx, message);
                case "CHECK_IN":       return continueCheckIn(email, ctx, message);
                case "CHECK_OUT":      return continueCheckOut(email, ctx, message);
                case "UPDATE_PROFILE": return continueUpdateProfile(email, ctx, message);
                case "CREATE_GOAL":    return continuteCreateGoal(email, ctx, message);
                case "APPROVE_LEAVE":  return continueApproveRejectLeave(email, ctx, message, "APPROVED");
                case "REJECT_LEAVE":   return continueApproveRejectLeave(email, ctx, message, "REJECTED");
                default:
                    activeFlows.remove(email);
                    return reply("Something went wrong. Let's start over.",
                            "Help", "Apply Leave", "My Profile");
            }
        } catch (Exception e) {
            activeFlows.remove(email);
            return reply("Sorry, an error occurred: " + e.getMessage() + "\nPlease try again.",
                    "Help", "Apply Leave");
        }
    }

    // ═══════════════════════════════════════════
    //  FLOW: APPLY LEAVE
    // ═══════════════════════════════════════════

    private AIChatResponse startApplyLeaveFlow(String email, String message) {
        ConversationContext ctx = new ConversationContext("APPLY_LEAVE");
        String lower = message.toLowerCase();

        // Pre-parse dates from the initial message
        List<LocalDate> dates = extractDates(message);
        if (dates.size() >= 1) {
            ctx.data.put("startDate", dates.get(0).toString());
        }
        if (dates.size() >= 2) {
            ctx.data.put("endDate", dates.get(1).toString());
        }

        // Pre-parse leave type
        Integer typeId = detectLeaveTypeFromMessage(lower);
        if (typeId != null) {
            ctx.data.put("leaveTypeId", typeId.toString());
            try {
                List<LeaveType> types = leaveService.getAllLeaveType();
                types.stream().filter(t -> t.getLeaveTypeId().equals(typeId)).findFirst()
                        .ifPresent(t -> ctx.data.put("leaveTypeName", t.getLeaveTypeName()));
            } catch (Exception ignored) {}
        }

        // Pre-parse reason
        String reason = extractReason(message);
        if (reason != null) {
            ctx.data.put("reason", reason);
        }

        // Determine what we still need
        activeFlows.put(email, ctx);
        return askNextLeaveField(ctx);
    }

    private AIChatResponse continueApplyLeave(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();
        int step = ctx.step;

        // Step 0: waiting for leave type
        if (step == 0 && !ctx.data.containsKey("leaveTypeId")) {
            Integer typeId = detectLeaveTypeFromInput(lower);
            if (typeId == null) {
                return reply("I didn't recognize that leave type. Please choose one:",
                        getLeaveTypeNames().toArray(new String[0]));
            }
            ctx.data.put("leaveTypeId", typeId.toString());
            try {
                List<LeaveType> types = leaveService.getAllLeaveType();
                types.stream().filter(t -> t.getLeaveTypeId().equals(typeId)).findFirst()
                        .ifPresent(t -> ctx.data.put("leaveTypeName", t.getLeaveTypeName()));
            } catch (Exception ignored) {
                ctx.data.put("leaveTypeName", "Leave Type #" + typeId);
            }
            return askNextLeaveField(ctx);
        }

        // Step 1: waiting for start date
        if (step == 1 && !ctx.data.containsKey("startDate")) {
            LocalDate date = parseNaturalDate(msg);
            if (date == null) {
                return reply("I couldn't understand that date. Please try a format like:\n• March 15\n• 2026-03-15\n• tomorrow\n• next Monday");
            }
            if (date.isBefore(LocalDate.now())) {
                return reply("That date is in the past. Please provide a future date.");
            }
            ctx.data.put("startDate", date.toString());
            return askNextLeaveField(ctx);
        }

        // Step 2: waiting for end date
        if (step == 2 && !ctx.data.containsKey("endDate")) {
            if (lower.equals("same day") || lower.equals("single day") || lower.equals("1 day")
                    || lower.equals("one day") || lower.equals("same")) {
                ctx.data.put("endDate", ctx.data.get("startDate"));
            } else {
                LocalDate date = parseNaturalDate(msg);
                if (date == null) {
                    return reply("I couldn't understand that date. Please try again or select:",
                            "Same day");
                }
                LocalDate start = LocalDate.parse(ctx.data.get("startDate"));
                if (date.isBefore(start)) {
                    return reply("End date can't be before start date (" + formatDate(start) + "). Please try again.",
                            "Same day");
                }
                ctx.data.put("endDate", date.toString());
            }
            return askNextLeaveField(ctx);
        }

        // Step 3: waiting for reason
        if (step == 3 && !ctx.data.containsKey("reason")) {
            if (msg.length() < 2) {
                return reply("Please provide a brief reason for your leave.");
            }
            ctx.data.put("reason", msg);
            return askNextLeaveField(ctx);
        }

        // Step 4: confirmation
        if (step == 4) {
            if (lower.contains("confirm") || lower.contains("yes") || lower.contains("submit")
                    || lower.contains("apply") || lower.contains("proceed")) {
                return executeApplyLeave(email, ctx);
            } else if (lower.contains("cancel") || lower.contains("no") || lower.contains("discard")) {
                activeFlows.remove(email);
                return reply("Leave application cancelled. What else can I help with?",
                        "Apply Leave", "My Leaves", "Help");
            } else {
                return reply("Please confirm or cancel your leave application.",
                        "✅ Confirm", "❌ Cancel");
            }
        }

        return askNextLeaveField(ctx);
    }

    private AIChatResponse askNextLeaveField(ConversationContext ctx) {
        // Check what's missing and ask for it
        if (!ctx.data.containsKey("leaveTypeId")) {
            ctx.step = 0;
            StringBuilder sb = new StringBuilder("Sure! I'll help you apply for leave.");
            if (ctx.data.containsKey("startDate")) {
                sb.append("\n📅 Starting: ").append(formatDate(LocalDate.parse(ctx.data.get("startDate"))));
            }
            sb.append("\n\nWhat type of leave would you like?");
            return reply(sb.toString(), getLeaveTypeNames().toArray(new String[0]));
        }

        if (!ctx.data.containsKey("startDate")) {
            ctx.step = 1;
            return reply("Got it, " + ctx.data.get("leaveTypeName") + ".\n\nFrom which date?\n(e.g., March 15, tomorrow, next Monday)",
                    "Tomorrow", "Next Monday");
        }

        if (!ctx.data.containsKey("endDate")) {
            ctx.step = 2;
            String startStr = formatDate(LocalDate.parse(ctx.data.get("startDate")));
            return reply(ctx.data.get("leaveTypeName") + " from " + startStr + ".\n\nTill which date?",
                    "Same day", formatDate(LocalDate.parse(ctx.data.get("startDate")).plusDays(1)),
                    formatDate(LocalDate.parse(ctx.data.get("startDate")).plusDays(2)));
        }

        if (!ctx.data.containsKey("reason")) {
            ctx.step = 3;
            return reply("What's the reason for your leave?",
                    "Personal work", "Not feeling well", "Family commitment", "Medical appointment");
        }

        // All data collected → show confirmation
        ctx.step = 4;
        LocalDate start = LocalDate.parse(ctx.data.get("startDate"));
        LocalDate end = LocalDate.parse(ctx.data.get("endDate"));
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;

        String confirmation = String.format(
                "Please confirm your leave application:\n\n" +
                "📋 Type: %s\n" +
                "📅 From: %s\n" +
                "📅 To: %s\n" +
                "📆 Days: %d\n" +
                "📝 Reason: %s",
                ctx.data.get("leaveTypeName"),
                formatDate(start), formatDate(end), days,
                ctx.data.get("reason"));

        return reply(confirmation, "✅ Confirm", "❌ Cancel");
    }

    private AIChatResponse executeApplyLeave(String email, ConversationContext ctx) {
        activeFlows.remove(email);
        try {
            LeaveApplyRequest request = new LeaveApplyRequest();
            request.setLeaveTypeId(Integer.parseInt(ctx.data.get("leaveTypeId")));
            request.setStartDate(LocalDate.parse(ctx.data.get("startDate")));
            request.setEndDate(LocalDate.parse(ctx.data.get("endDate")));
            request.setReason(ctx.data.get("reason"));

            LeaveApplication leave = leaveService.applyLeave(email, request);

            String successMsg = String.format(
                    "✅ Leave applied successfully!\n\n" +
                    "• Leave ID: #%d\n" +
                    "• Type: %s\n" +
                    "• From: %s\n" +
                    "• To: %s\n" +
                    "• Working Days: %d\n" +
                    "• Status: %s\n\n" +
                    "Your manager has been notified.",
                    leave.getLeaveId(), leave.getLeaveType().getLeaveTypeName(),
                    formatDate(leave.getStartDate()), formatDate(leave.getEndDate()),
                    leave.getTotalDays(), leave.getStatus());

            return AIChatResponse.builder()
                    .reply(successMsg)
                    .action("APPLY_LEAVE")
                    .actionPerformed(true)
                    .quickReplies(List.of("My Leaves", "Leave Balance", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("❌ Could not apply leave: " + e.getMessage(),
                    "Try Again", "Leave Balance", "Help");
        }
    }

    // ═══════════════════════════════════════════
    //  FLOW: CANCEL LEAVE
    // ═══════════════════════════════════════════

    private AIChatResponse startCancelLeaveFlow(String email) {
        try {
            Page<LeaveApplication> pending = leaveService.getMyLeaves(email, LeaveStatus.PENDING,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "appliedDate")));

            if (pending.isEmpty()) {
                return reply("You don't have any pending leave applications to cancel.",
                        "My Leaves", "Apply Leave", "Help");
            }

            ConversationContext ctx = new ConversationContext("CANCEL_LEAVE");
            activeFlows.put(email, ctx);

            StringBuilder sb = new StringBuilder("Here are your pending leave applications:\n\n");
            List<String> quickReplies = new ArrayList<>();
            for (LeaveApplication la : pending.getContent()) {
                sb.append(String.format("🔹 #%d — %s (%s to %s) - %s\n",
                        la.getLeaveId(), la.getLeaveType().getLeaveTypeName(),
                        formatDate(la.getStartDate()), formatDate(la.getEndDate()),
                        la.getReason() != null ? la.getReason() : "No reason"));
                quickReplies.add("#" + la.getLeaveId());
            }
            sb.append("\nWhich leave would you like to cancel? (Enter the ID number)");

            return reply(sb.toString(), quickReplies.toArray(new String[0]));
        } catch (Exception e) {
            return reply("Error fetching your leaves: " + e.getMessage(), "Help");
        }
    }

    private AIChatResponse continueCancelLeave(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();

        if (ctx.step == 0) {
            // Extract leave ID
            Pattern p = Pattern.compile("\\d+");
            Matcher m = p.matcher(msg);
            if (!m.find()) {
                return reply("Please enter a valid leave ID number (e.g., #5 or 5).");
            }
            String leaveId = m.group();
            ctx.data.put("leaveId", leaveId);
            ctx.step = 1;
            return reply("Cancel leave #" + leaveId + "? This cannot be undone.",
                    "✅ Yes, cancel it", "❌ No, keep it");
        }

        if (ctx.step == 1) {
            if (lower.contains("yes") || lower.contains("confirm") || lower.contains("cancel it")) {
                activeFlows.remove(email);
                try {
                    Integer leaveId = Integer.parseInt(ctx.data.get("leaveId"));
                    LeaveApplication cancelled = leaveService.cancelLeave(email, leaveId);
                    return AIChatResponse.builder()
                            .reply("✅ Leave #" + leaveId + " has been cancelled successfully.\nStatus: " + cancelled.getStatus())
                            .action("CANCEL_LEAVE")
                            .actionPerformed(true)
                            .quickReplies(List.of("My Leaves", "Apply Leave", "Help"))
                            .build();
                } catch (Exception e) {
                    return reply("❌ Could not cancel: " + e.getMessage(), "My Leaves", "Help");
                }
            } else {
                activeFlows.remove(email);
                return reply("Okay, your leave remains unchanged.", "My Leaves", "Help");
            }
        }

        activeFlows.remove(email);
        return reply("Something went wrong. Please try again.", "Cancel Leave", "Help");
    }

    // ═══════════════════════════════════════════
    //  FLOW: CHECK IN
    // ═══════════════════════════════════════════

    private AIChatResponse startCheckInFlow(String email) {
        // Check if already checked in
        try {
            var status = attendanceService.getTodayStatus(email);
            if (status.getCheckInTime() != null) {
                return reply("You've already checked in today at " + status.getCheckInTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) + ".",
                        "Check Out", "My Attendance", "Help");
            }
        } catch (Exception ignored) {}

        ConversationContext ctx = new ConversationContext("CHECK_IN");
        activeFlows.put(email, ctx);

        return reply("Ready to check in for today (" + formatDate(LocalDate.now()) + ")?\nWould you like to add any notes?",
                "✅ Check In Now", "Add notes first");
    }

    private AIChatResponse continueCheckIn(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();

        if (ctx.step == 0) {
            if (lower.contains("check in now") || lower.contains("yes") || lower.contains("proceed")
                    || lower.contains("check in") || lower.contains("confirm")) {
                return executeCheckIn(email, null);
            } else if (lower.contains("add notes") || lower.contains("note")) {
                ctx.step = 1;
                return reply("Enter your notes for today's check-in:");
            } else {
                ctx.data.put("notes", msg);
                return executeCheckIn(email, msg);
            }
        }

        if (ctx.step == 1) {
            return executeCheckIn(email, msg);
        }

        activeFlows.remove(email);
        return reply("Something went wrong. Please try again.", "Check In", "Help");
    }

    private AIChatResponse executeCheckIn(String email, String notes) {
        activeFlows.remove(email);
        try {
            CheckInRequest request = new CheckInRequest();
            request.setNotes(notes);
            var result = attendanceService.checkIn(email, request, "chatbot");

            String time = result.getCheckInTime() != null
                    ? result.getCheckInTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "now";

            return AIChatResponse.builder()
                    .reply("✅ Checked in successfully!\n\n• Time: " + time + "\n• Date: " + formatDate(LocalDate.now())
                            + "\n• Status: " + result.getStatus()
                            + (Boolean.TRUE.equals(result.getIsLate()) ? "\n⚠️ Marked as late arrival" : ""))
                    .action("CHECK_IN")
                    .actionPerformed(true)
                    .quickReplies(List.of("Check Out", "My Attendance", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("❌ Check-in failed: " + e.getMessage(), "My Attendance", "Help");
        }
    }

    // ═══════════════════════════════════════════
    //  FLOW: CHECK OUT
    // ═══════════════════════════════════════════

    private AIChatResponse startCheckOutFlow(String email) {
        try {
            var status = attendanceService.getTodayStatus(email);
            if (status.getCheckInTime() == null || "NOT_CHECKED_IN".equals(status.getStatus())) {
                return reply("You haven't checked in yet today. Please check in first.",
                        "Check In", "Help");
            }
            if (status.getCheckOutTime() != null) {
                return reply("You've already checked out today at " + status.getCheckOutTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) + ".",
                        "My Attendance", "Help");
            }
        } catch (Exception ignored) {}

        ConversationContext ctx = new ConversationContext("CHECK_OUT");
        activeFlows.put(email, ctx);

        return reply("Ready to check out for today?\nWould you like to add any notes?",
                "✅ Check Out Now", "Add notes first");
    }

    private AIChatResponse continueCheckOut(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();

        if (ctx.step == 0) {
            if (lower.contains("check out now") || lower.contains("yes") || lower.contains("proceed")
                    || lower.contains("check out") || lower.contains("confirm")) {
                return executeCheckOut(email, null);
            } else if (lower.contains("add notes") || lower.contains("note")) {
                ctx.step = 1;
                return reply("Enter your notes for today's check-out:");
            } else {
                return executeCheckOut(email, msg);
            }
        }

        if (ctx.step == 1) {
            return executeCheckOut(email, msg);
        }

        activeFlows.remove(email);
        return reply("Something went wrong. Please try again.", "Check Out", "Help");
    }

    private AIChatResponse executeCheckOut(String email, String notes) {
        activeFlows.remove(email);
        try {
            CheckOutRequest request = new CheckOutRequest();
            request.setNotes(notes);
            var result = attendanceService.checkOut(email, request, "chatbot");

            String time = result.getCheckOutTime() != null
                    ? result.getCheckOutTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "now";
            String hours = result.getTotalHours() != null ? String.format("%.1f", result.getTotalHours()) : "N/A";

            return AIChatResponse.builder()
                    .reply("✅ Checked out successfully!\n\n• Time: " + time + "\n• Total Hours: " + hours
                            + (Boolean.TRUE.equals(result.getIsEarlyDeparture()) ? "\n⚠️ Marked as early departure" : "")
                            + "\n\nGreat work today! 👏")
                    .action("CHECK_OUT")
                    .actionPerformed(true)
                    .quickReplies(List.of("My Attendance", "My Dashboard", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("❌ Check-out failed: " + e.getMessage(), "My Attendance", "Help");
        }
    }

    // ═══════════════════════════════════════════
    //  FLOW: UPDATE PROFILE
    // ═══════════════════════════════════════════

    private AIChatResponse startUpdateProfileFlow(String email) {
        ConversationContext ctx = new ConversationContext("UPDATE_PROFILE");
        activeFlows.put(email, ctx);

        return reply("What would you like to update?\n\n• Phone number\n• Address\n• Emergency contact name\n• Emergency contact phone",
                "Phone", "Address", "Emergency Contact Name", "Emergency Contact Phone");
    }

    private AIChatResponse continueUpdateProfile(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();

        if (ctx.step == 0) {
            String field = null;
            if (lower.contains("phone") && !lower.contains("emergency")) field = "phone";
            else if (lower.contains("address")) field = "address";
            else if (lower.contains("emergency") && lower.contains("name")) field = "emergencyContactName";
            else if (lower.contains("emergency") && lower.contains("phone")) field = "emergencyContactPhone";
            else if (lower.contains("emergency")) field = "emergencyContactName";

            if (field == null) {
                return reply("Please choose what to update:", "Phone", "Address", "Emergency Contact Name", "Emergency Contact Phone");
            }

            ctx.data.put("field", field);
            ctx.step = 1;

            String fieldLabel = switch (field) {
                case "phone" -> "phone number";
                case "address" -> "address";
                case "emergencyContactName" -> "emergency contact name";
                case "emergencyContactPhone" -> "emergency contact phone";
                default -> field;
            };
            return reply("Enter your new " + fieldLabel + ":");
        }

        if (ctx.step == 1) {
            ctx.data.put("value", msg);
            ctx.step = 2;
            return reply("Update " + ctx.data.get("field") + " to: " + msg + "?",
                    "✅ Confirm", "❌ Cancel");
        }

        if (ctx.step == 2) {
            if (lower.contains("confirm") || lower.contains("yes")) {
                return executeUpdateProfile(email, ctx);
            }
            activeFlows.remove(email);
            return reply("Profile update cancelled.", "My Profile", "Help");
        }

        activeFlows.remove(email);
        return reply("Something went wrong. Please try again.", "Update Profile", "Help");
    }

    private AIChatResponse executeUpdateProfile(String email, ConversationContext ctx) {
        activeFlows.remove(email);
        try {
            UpdateProfileRequest request = new UpdateProfileRequest();
            String field = ctx.data.get("field");
            String value = ctx.data.get("value");

            switch (field) {
                case "phone" -> request.setPhone(value);
                case "address" -> request.setAddress(value);
                case "emergencyContactName" -> request.setEmergencyContactName(value);
                case "emergencyContactPhone" -> request.setEmergencyContactPhone(value);
            }

            employeeService.updateProfileWithResponse(email, request);

            return AIChatResponse.builder()
                    .reply("✅ Profile updated successfully!\n\n• " + field + " → " + value)
                    .action("UPDATE_PROFILE")
                    .actionPerformed(true)
                    .quickReplies(List.of("My Profile", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("❌ Could not update profile: " + e.getMessage(), "My Profile", "Help");
        }
    }

    // ═══════════════════════════════════════════
    //  FLOW: CREATE GOAL
    // ═══════════════════════════════════════════

    private AIChatResponse startCreateGoalFlow(String email, String message) {
        ConversationContext ctx = new ConversationContext("CREATE_GOAL");
        activeFlows.put(email, ctx);
        ctx.step = 0;
        return reply("Let's create a new goal! What's the title?");
    }

    private AIChatResponse continuteCreateGoal(String email, ConversationContext ctx, String msg) {
        String lower = msg.toLowerCase().trim();

        if (ctx.step == 0) {
            ctx.data.put("title", msg);
            ctx.step = 1;
            return reply("Brief description? (or type 'skip' to leave it empty)", "Skip");
        }

        if (ctx.step == 1) {
            if (!lower.equals("skip")) {
                ctx.data.put("description", msg);
            }
            ctx.step = 2;
            return reply("What's the deadline? (e.g., March 30, 2026-06-30)");
        }

        if (ctx.step == 2) {
            LocalDate deadline = parseNaturalDate(msg);
            if (deadline == null) {
                return reply("I couldn't parse that date. Please try: March 30 or 2026-06-30");
            }
            ctx.data.put("deadline", deadline.toString());
            ctx.step = 3;
            return reply("Priority level?", "HIGH", "MEDIUM", "LOW");
        }

        if (ctx.step == 3) {
            String priority = lower.contains("high") ? "HIGH" : lower.contains("low") ? "LOW" : "MEDIUM";
            ctx.data.put("priority", priority);
            ctx.step = 4;

            String confirmation = String.format(
                    "Please confirm your new goal:\n\n" +
                    "🎯 Title: %s\n" +
                    "📝 Description: %s\n" +
                    "📅 Deadline: %s\n" +
                    "⚡ Priority: %s",
                    ctx.data.get("title"),
                    ctx.data.getOrDefault("description", "N/A"),
                    formatDate(LocalDate.parse(ctx.data.get("deadline"))),
                    priority);
            return reply(confirmation, "✅ Confirm", "❌ Cancel");
        }

        if (ctx.step == 4) {
            if (lower.contains("confirm") || lower.contains("yes")) {
                return executeCreateGoal(email, ctx);
            }
            activeFlows.remove(email);
            return reply("Goal creation cancelled.", "My Goals", "Help");
        }

        activeFlows.remove(email);
        return reply("Something went wrong. Please try again.", "Create Goal", "Help");
    }

    private AIChatResponse executeCreateGoal(String email, ConversationContext ctx) {
        activeFlows.remove(email);
        try {
            GoalRequest request = new GoalRequest();
            request.setTitle(ctx.data.get("title"));
            request.setDescription(ctx.data.getOrDefault("description", null));
            request.setDeadline(LocalDate.parse(ctx.data.get("deadline")));
            request.setPriority(ctx.data.get("priority"));

            Goal goal = performanceService.createGoal(email, request);

            return AIChatResponse.builder()
                    .reply("✅ Goal created successfully!\n\n• Goal ID: #" + goal.getGoalId()
                            + "\n• Title: " + goal.getTitle()
                            + "\n• Priority: " + goal.getPriority()
                            + "\n• Deadline: " + formatDate(goal.getDeadline())
                            + "\n• Status: " + goal.getStatus())
                    .action("CREATE_GOAL")
                    .actionPerformed(true)
                    .quickReplies(List.of("My Goals", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("❌ Could not create goal: " + e.getMessage(), "My Goals", "Help");
        }
    }

    // ═══════════════════════════════════════════
    //  SINGLE-TURN: DATA QUERIES
    // ═══════════════════════════════════════════

    private AIChatResponse getLeaveBalance(String email) {
        List<LeaveBalance> balances = leaveService.getMyLeaveBalance(email);
        if (balances.isEmpty()) {
            return reply("No leave balance records found.", "Apply Leave", "Help");
        }

        StringBuilder sb = new StringBuilder("📊 Your Leave Balance:\n\n");
        for (LeaveBalance b : balances) {
            int available = b.getAvailableBalance();
            String bar = progressBar(b.getUsedLeaves(), b.getTotalLeaves());
            sb.append(String.format("• %s: %d available (%d used / %d total) %s\n",
                    b.getLeaveType().getLeaveTypeName(), available,
                    b.getUsedLeaves(), b.getTotalLeaves(), bar));
        }
        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_LEAVE_BALANCE")
                .actionPerformed(true)
                .quickReplies(List.of("Apply Leave", "My Leaves", "Help"))
                .build();
    }

    private AIChatResponse getMyLeaves(String email) {
        Page<LeaveApplication> leaves = leaveService.getMyLeaves(email, null,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "appliedDate")));

        if (leaves.isEmpty()) {
            return reply("You don't have any leave applications yet.", "Apply Leave", "Help");
        }

        StringBuilder sb = new StringBuilder("📋 Your Leave Applications:\n\n");
        for (LeaveApplication la : leaves.getContent()) {
            String statusIcon = switch (la.getStatus()) {
                case PENDING -> "🟡";
                case APPROVED -> "🟢";
                case REJECTED -> "🔴";
                case CANCELLED -> "⚪";
            };
            sb.append(String.format("%s #%d — %s | %s to %s | %s\n",
                    statusIcon, la.getLeaveId(), la.getLeaveType().getLeaveTypeName(),
                    formatDate(la.getStartDate()), formatDate(la.getEndDate()),
                    la.getStatus()));
        }

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_MY_LEAVES")
                .actionPerformed(true)
                .quickReplies(List.of("Apply Leave", "Cancel Leave", "Leave Balance", "Help"))
                .build();
    }

    private AIChatResponse getAttendanceToday(String email) {
        var status = attendanceService.getTodayStatus(email);

        StringBuilder sb = new StringBuilder("📅 Today's Attendance (" + formatDate(LocalDate.now()) + "):\n\n");
        sb.append("• Status: ").append(status.getStatus()).append("\n");
        sb.append("• Check-in: ").append(status.getCheckInTime() != null
                ? status.getCheckInTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "Not yet").append("\n");
        sb.append("• Check-out: ").append(status.getCheckOutTime() != null
                ? status.getCheckOutTime().toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "Not yet").append("\n");
        if (status.getTotalHours() != null) {
            sb.append("• Hours: ").append(String.format("%.1f", status.getTotalHours())).append("\n");
        }

        List<String> actions = new ArrayList<>();
        if (status.getCheckInTime() == null || "NOT_CHECKED_IN".equals(status.getStatus())) {
            actions.add("Check In");
        } else if (status.getCheckOutTime() == null) {
            actions.add("Check Out");
        }
        actions.add("Attendance Summary");
        actions.add("Help");

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_ATTENDANCE_TODAY")
                .actionPerformed(true)
                .quickReplies(actions)
                .build();
    }

    private AIChatResponse getAttendanceSummary(String email) {
        AttendanceSummaryResponse s = attendanceService.getMySummary(email, null, null);

        String summary = String.format(
                "📊 Attendance Summary — %s %d\n\n" +
                "• Present: %d days\n" +
                "• Absent: %d days\n" +
                "• Half Day: %d\n" +
                "• On Leave: %d\n" +
                "• Late Arrivals: %d\n" +
                "• Early Departures: %d\n" +
                "• Total Hours: %s",
                s.getMonth(), s.getYear(),
                s.getTotalPresent(), s.getTotalAbsent(), s.getTotalHalfDay(), s.getTotalOnLeave(),
                s.getTotalLateArrivals(), s.getTotalEarlyDepartures(),
                s.getTotalHoursWorked() != null ? String.format("%.1f", s.getTotalHoursWorked()) : "0");

        return AIChatResponse.builder()
                .reply(summary)
                .action("GET_ATTENDANCE_SUMMARY")
                .actionPerformed(true)
                .quickReplies(List.of("Today's Attendance", "Check In", "Help"))
                .build();
    }

    private AIChatResponse getDashboard(String email) {
        EmployeeDashboardResponse d = dashboardService.getEmployeeDashboard(email);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🏠 Dashboard — %s (%s)\n\n", d.getEmployeeName(), d.getEmployeeCode()));
        sb.append(String.format("• Department: %s\n• Designation: %s\n", d.getDepartmentName(), d.getDesignationTitle()));
        sb.append(String.format("• Pending Leaves: %d\n• Approved Leaves: %d\n• Notifications: %d\n",
                d.getPendingLeaveRequests(), d.getApprovedLeaves(), d.getUnreadNotifications()));

        if (d.getLeaveBalances() != null && !d.getLeaveBalances().isEmpty()) {
            sb.append("\n📊 Leave Balance:\n");
            for (var lb : d.getLeaveBalances()) {
                sb.append(String.format("  • %s: %d available\n", lb.getLeaveTypeName(), lb.getAvailableBalance()));
            }
        }
        if (d.getUpcomingHolidays() != null && !d.getUpcomingHolidays().isEmpty()) {
            sb.append("\n🗓️ Upcoming Holidays:\n");
            for (var h : d.getUpcomingHolidays()) {
                sb.append(String.format("  • %s — %s\n", formatDate(h.getHolidayDate()), h.getHolidayName()));
            }
        }

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_MY_DASHBOARD")
                .actionPerformed(true)
                .quickReplies(List.of("My Profile", "Leave Balance", "Check In", "Help"))
                .build();
    }

    private AIChatResponse getAnnouncements() {
        Page<Announcement> page = announcementService.getActiveAnnouncements(PageRequest.of(0, 5));
        if (page.isEmpty()) {
            return reply("No active announcements at the moment.", "My Dashboard", "Help");
        }

        StringBuilder sb = new StringBuilder("📢 Recent Announcements:\n\n");
        for (Announcement a : page.getContent()) {
            sb.append(String.format("📌 %s\n%s\n%s\n\n",
                    a.getTitle(),
                    a.getContent() != null && a.getContent().length() > 120
                            ? a.getContent().substring(0, 120) + "..." : a.getContent(),
                    a.getCreatedAt() != null ? formatDate(a.getCreatedAt().toLocalDate()) : ""));
        }

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_ANNOUNCEMENTS")
                .actionPerformed(true)
                .quickReplies(List.of("My Dashboard", "Holidays", "Help"))
                .build();
    }

    private AIChatResponse getProfile(String email) {
        var p = employeeService.getEmployeeProfileByEmail(email);

        String profile = String.format(
                "👤 Your Profile\n\n" +
                "• Name: %s %s\n" +
                "• Code: %s\n" +
                "• Email: %s\n" +
                "• Phone: %s\n" +
                "• Department: %s\n" +
                "• Designation: %s\n" +
                "• Role: %s\n" +
                "• Joining Date: %s\n" +
                "• Status: %s",
                p.getFirstName(), p.getLastName(),
                p.getEmployeeCode(), p.getEmail(),
                p.getPhone() != null ? p.getPhone() : "Not set",
                p.getDepartmentName() != null ? p.getDepartmentName() : "N/A",
                p.getDesignationTitle() != null ? p.getDesignationTitle() : "N/A",
                p.getRole(),
                p.getJoiningDate() != null ? formatDate(p.getJoiningDate()) : "N/A",
                p.getIsActive() ? "✅ Active" : "❌ Inactive");

        return AIChatResponse.builder()
                .reply(profile)
                .action("GET_MY_PROFILE")
                .actionPerformed(true)
                .quickReplies(List.of("Update Profile", "My Dashboard", "Help"))
                .build();
    }

    private AIChatResponse getHolidays() {
        List<Holiday> holidays = leaveService.getHolidays(LocalDate.now().getYear());
        if (holidays.isEmpty()) {
            return reply("No holidays found for " + LocalDate.now().getYear() + ".", "Help");
        }

        StringBuilder sb = new StringBuilder("🗓️ Holidays — " + LocalDate.now().getYear() + "\n\n");
        LocalDate today = LocalDate.now();
        for (Holiday h : holidays) {
            String icon = h.getHolidayDate().isBefore(today) ? "✓" : "•";
            sb.append(String.format("%s %s — %s\n", icon, formatDate(h.getHolidayDate()), h.getHolidayName()));
        }

        long upcoming = holidays.stream().filter(h -> !h.getHolidayDate().isBefore(today)).count();
        sb.append("\n").append(upcoming).append(" upcoming holidays remaining");

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_HOLIDAYS")
                .actionPerformed(true)
                .quickReplies(List.of("Apply Leave", "My Dashboard", "Help"))
                .build();
    }

    private AIChatResponse getMyGoals(String email) {
        Page<Goal> goals = performanceService.getMyGoals(email, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        if (goals.isEmpty()) {
            return reply("You don't have any goals yet.", "Create Goal", "Help");
        }

        StringBuilder sb = new StringBuilder("🎯 Your Goals:\n\n");
        for (Goal g : goals.getContent()) {
            String statusIcon = switch (g.getStatus()) {
                case NOT_STARTED -> "⬜";
                case IN_PROGRESS -> "🔵";
                case COMPLETED -> "✅";
            };
            sb.append(String.format("%s %s — %s | %d%% | Due: %s\n",
                    statusIcon, g.getTitle(), g.getPriority(), g.getProgress(),
                    formatDate(g.getDeadline())));
        }

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_MY_GOALS")
                .actionPerformed(true)
                .quickReplies(List.of("Create Goal", "My Reviews", "Help"))
                .build();
    }

    private AIChatResponse getMyReviews(String email) {
        Page<PerformanceReview> reviews = performanceService.getMyReviews(email, null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));

        if (reviews.isEmpty()) {
            return reply("No performance reviews found.", "My Goals", "Help");
        }

        StringBuilder sb = new StringBuilder("📝 Your Performance Reviews:\n\n");
        for (PerformanceReview r : reviews.getContent()) {
            sb.append(String.format("• %s — %s | Self: %s | Manager: %s\n",
                    r.getReviewPeriod(), r.getStatus(),
                    r.getSelfAssessmentRating() != null ? r.getSelfAssessmentRating().toString() : "N/A",
                    r.getManagerRating() != null ? r.getManagerRating().toString() : "Pending"));
        }

        return AIChatResponse.builder()
                .reply(sb.toString())
                .action("GET_MY_REVIEWS")
                .actionPerformed(true)
                .quickReplies(List.of("My Goals", "My Dashboard", "Help"))
                .build();
    }

    private AIChatResponse getNotifications(String email) {
        long unread = notificationService.getUnreadCount(email);

        String msg = unread == 0
                ? "🔔 You're all caught up! No unread notifications."
                : "🔔 You have " + unread + " unread notification" + (unread > 1 ? "s" : "") + ".\nCheck the notification bell for details.";

        return AIChatResponse.builder()
                .reply(msg)
                .action("GET_NOTIFICATIONS")
                .actionPerformed(true)
                .quickReplies(List.of("My Dashboard", "My Leaves", "Help"))
                .build();
    }

    private AIChatResponse getGreeting(String email, Role role) {
        String name;
        try {
            var p = employeeService.getEmployeeProfileByEmail(email);
            name = p.getFirstName();
        } catch (Exception e) {
            name = "there";
        }

        int hour = LocalTime.now().getHour();
        String timeGreeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String roleLabel = role == Role.ADMIN ? "Admin" : role == Role.MANAGER ? "Manager" : "Employee";

        StringBuilder sb = new StringBuilder();
        sb.append(timeGreeting).append(", ").append(name).append("! 👋\n");
        sb.append("Role: ").append(roleLabel).append("\n\n");
        sb.append("I'm your WorkForce AI assistant. Here's what I can help with:\n\n");
        sb.append("📝 Apply or cancel leaves\n");
        sb.append("⏰ Check in/out attendance\n");
        sb.append("📊 View leave balance, dashboard\n");
        sb.append("👤 View/update your profile\n");
        sb.append("🎯 Goals & performance reviews\n");
        if (role == Role.MANAGER || role == Role.ADMIN) {
            sb.append("\n👥 TEAM ACTIONS:\n");
            sb.append("📋 Team Leaves — view & approve/reject\n");
            sb.append("📅 Team Attendance — who's present today\n");
            sb.append("📝 Team Reviews — team performance\n");
        }
        if (role == Role.ADMIN) {
            sb.append("\n🔧 ADMIN ACTIONS:\n");
            sb.append("🏢 Admin Dashboard — system-wide stats\n");
            sb.append("📋 All Leaves — company-wide leave list\n");
        }
        sb.append("\nWhat would you like to do?");

        return reply(sb.toString(), getDefaultQuickReplies(role));
    }

    private AIChatResponse getHelp(Role role) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 Here's what I can do for you:\n\n");
        sb.append("📝 LEAVE MANAGEMENT\n");
        sb.append("  • Apply Leave — multi-step guided flow\n");
        sb.append("  • Cancel Leave — cancel pending/approved leaves\n");
        sb.append("  • Leave Balance — view your leave balance\n");
        sb.append("  • My Leaves — list your leave applications\n\n");
        sb.append("⏰ ATTENDANCE\n");
        sb.append("  • Check In / Check Out\n");
        sb.append("  • Today's Attendance — today's status\n");
        sb.append("  • Attendance Summary — monthly summary\n\n");
        sb.append("👤 PROFILE & INFO\n");
        sb.append("  • My Profile / Update Profile\n");
        sb.append("  • My Dashboard — overview\n\n");
        sb.append("🎯 GOALS & REVIEWS\n");
        sb.append("  • My Goals / Create Goal\n");
        sb.append("  • My Reviews — performance reviews\n\n");
        sb.append("📢 OTHER\n");
        sb.append("  • Announcements • Holidays • Notifications\n");

        if (role == Role.MANAGER || role == Role.ADMIN) {
            sb.append("\n👥 TEAM (Manager/Admin):\n");
            sb.append("  • Team Leaves — pending team requests\n");
            sb.append("  • Approve Leave / Reject Leave\n");
            sb.append("  • Team Attendance — today's team status\n");
            sb.append("  • Team Reviews — team performance\n");
        }
        if (role == Role.ADMIN) {
            sb.append("\n🔧 ADMIN ONLY:\n");
            sb.append("  • Admin Dashboard — workforce stats\n");
            sb.append("  • All Leaves — company-wide list\n");
        }
        sb.append("\n💡 Just type naturally — I understand!");

        return reply(sb.toString(), getDefaultQuickReplies(role));
    }

    // ═══════════════════════════════════════════
    //  MANAGER / ADMIN ACTIONS
    // ═══════════════════════════════════════════

    private AIChatResponse getTeamLeaves(String email, Role role) {
        try {
            Page<LeaveApplication> leaves;
            if (role == Role.ADMIN) {
                leaves = leaveService.getAllLeaveApplications(LeaveStatus.PENDING,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "appliedDate")));
            } else {
                leaves = leaveService.getTeamLeaves(email, LeaveStatus.PENDING,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "appliedDate")));
            }
            if (leaves.isEmpty()) {
                return reply("✅ No pending leave requests from your team!",
                        getDefaultQuickReplies(role));
            }
            StringBuilder sb = new StringBuilder("📋 Pending Team Leaves:\n\n");
            for (LeaveApplication la : leaves.getContent()) {
                sb.append(String.format("🔹 #%d — %s %s\n   %s | %s to %s | %d day(s)\n   Reason: %s\n\n",
                        la.getLeaveId(),
                        la.getEmployee().getFirstName(), la.getEmployee().getLastName(),
                        la.getLeaveType().getLeaveTypeName(),
                        formatDate(la.getStartDate()), formatDate(la.getEndDate()),
                        la.getTotalDays(),
                        la.getReason() != null ? la.getReason() : "N/A"));
            }
            sb.append("Say 'approve leave' or 'reject leave' to take action.");
            return AIChatResponse.builder()
                    .reply(sb.toString()).action("GET_TEAM_LEAVES").actionPerformed(true)
                    .quickReplies(List.of("Approve Leave", "Reject Leave", "Team Attendance", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("Error: " + e.getMessage(), getDefaultQuickReplies(role));
        }
    }

    private AIChatResponse startApproveLeavFlow(String email, Role role) {
        ConversationContext ctx = new ConversationContext("APPROVE_LEAVE");
        activeFlows.put(email, ctx);
        return reply("Enter the Leave ID to approve (e.g. 5):");
    }

    private AIChatResponse startRejectLeaveFlow(String email, Role role) {
        ConversationContext ctx = new ConversationContext("REJECT_LEAVE");
        activeFlows.put(email, ctx);
        return reply("Enter the Leave ID to reject (e.g. 5):");
    }

    private AIChatResponse continueApproveRejectLeave(String email, ConversationContext ctx, String msg, String action) {
        String lower = msg.toLowerCase().trim();
        Role role = getUserRole(email);

        if (ctx.step == 0) {
            Pattern p = Pattern.compile("\\d+");
            Matcher m = p.matcher(msg);
            if (!m.find()) {
                return reply("Please enter a valid leave ID number.");
            }
            ctx.data.put("leaveId", m.group());
            if ("REJECTED".equals(action)) {
                ctx.step = 1;
                return reply("Reason for rejection?");
            }
            ctx.step = 2;
            return reply(action.equals("APPROVED") ? "Approve" : "Reject" + " leave #" + m.group() + "?",
                    "✅ Yes", "❌ No");
        }

        if (ctx.step == 1) {
            ctx.data.put("comments", msg);
            ctx.step = 2;
            return reply("Reject leave #" + ctx.data.get("leaveId") + " with reason: \"" + msg + "\"?",
                    "✅ Yes", "❌ No");
        }

        if (ctx.step == 2) {
            if (lower.contains("yes") || lower.contains("confirm") || lower.contains("✅")) {
                activeFlows.remove(email);
                try {
                    Integer leaveId = Integer.parseInt(ctx.data.get("leaveId"));
                    LeaveActionRequest req = new LeaveActionRequest();
                    req.setAction(action);
                    req.setComments(ctx.data.getOrDefault("comments", null));

                    LeaveApplication result;
                    if (role == Role.ADMIN) {
                        result = leaveService.adminActionLeave(email, leaveId, req);
                    } else {
                        result = leaveService.actionLeave(email, leaveId, req);
                    }
                    String emoji = "APPROVED".equals(action) ? "✅" : "❌";
                    return AIChatResponse.builder()
                            .reply(emoji + " Leave #" + leaveId + " has been " + action.toLowerCase() + ".\n" +
                                    "• Employee: " + result.getEmployee().getFirstName() + " " + result.getEmployee().getLastName() + "\n" +
                                    "• Type: " + result.getLeaveType().getLeaveTypeName() + "\n" +
                                    "• Dates: " + formatDate(result.getStartDate()) + " to " + formatDate(result.getEndDate()))
                            .action("APPROVED".equals(action) ? "APPROVE_LEAVE" : "REJECT_LEAVE")
                            .actionPerformed(true)
                            .quickReplies(List.of("Team Leaves", "Team Attendance", "Help"))
                            .build();
                } catch (Exception e) {
                    return reply("❌ Failed: " + e.getMessage(), "Team Leaves", "Help");
                }
            }
            activeFlows.remove(email);
            return reply("Cancelled.", getDefaultQuickReplies(role));
        }
        activeFlows.remove(email);
        return reply("Something went wrong.", getDefaultQuickReplies(role));
    }

    private AIChatResponse getTeamAttendance(String email, Role role) {
        try {
            List<AttendanceResponse> team = attendanceService.getTeamAttendanceToday(email);
            if (team.isEmpty()) {
                return reply("No team attendance records for today yet.",
                        getDefaultQuickReplies(role));
            }
            StringBuilder sb = new StringBuilder("👥 Team Attendance Today:\n\n");
            long present = team.stream().filter(a -> !"NOT_CHECKED_IN".equals(a.getStatus())).count();
            sb.append("Present: ").append(present).append(" / ").append(team.size()).append("\n\n");
            for (AttendanceResponse a : team) {
                String icon = "NOT_CHECKED_IN".equals(a.getStatus()) ? "⬜" :
                        a.getCheckOutTime() != null ? "✅" : "🟢";
                sb.append(String.format("%s %s — %s", icon, a.getEmployeeName(), a.getStatus()));
                if (a.getCheckInTime() != null) {
                    sb.append(" (in: ").append(a.getCheckInTime().toLocalTime().format(
                            DateTimeFormatter.ofPattern("hh:mm a"))).append(")");
                }
                sb.append("\n");
            }
            return AIChatResponse.builder()
                    .reply(sb.toString()).action("GET_TEAM_ATTENDANCE").actionPerformed(true)
                    .quickReplies(List.of("Team Leaves", "My Attendance", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("Error: " + e.getMessage(), getDefaultQuickReplies(role));
        }
    }

    private AIChatResponse getTeamReviews(String email, Role role) {
        try {
            Page<PerformanceReview> reviews = performanceService.getTeamReviews(email, null,
                    PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")));
            if (reviews.isEmpty()) {
                return reply("No team performance reviews found.", getDefaultQuickReplies(role));
            }
            StringBuilder sb = new StringBuilder("📝 Team Performance Reviews:\n\n");
            for (PerformanceReview r : reviews.getContent()) {
                sb.append(String.format("• %s %s — %s | %s | Self: %s\n",
                        r.getEmployee().getFirstName(), r.getEmployee().getLastName(),
                        r.getReviewPeriod(), r.getStatus(),
                        r.getSelfAssessmentRating() != null ? r.getSelfAssessmentRating().toString() : "N/A"));
            }
            return AIChatResponse.builder()
                    .reply(sb.toString()).action("GET_TEAM_REVIEWS").actionPerformed(true)
                    .quickReplies(List.of("Team Leaves", "My Reviews", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("Error: " + e.getMessage(), getDefaultQuickReplies(role));
        }
    }

    private AIChatResponse getAdminDashboard(String email, Role role) {
        try {
            DashboardResponse d = dashboardService.getDashboard();
            String summary = String.format(
                    "🏢 Admin Dashboard\n\n" +
                    "👥 Total Employees: %d\n" +
                    "  • Active: %d  |  Inactive: %d\n" +
                    "  • Admins: %d  |  Managers: %d  |  Employees: %d\n\n" +
                    "🏬 Departments: %d  |  Designations: %d\n" +
                    "📝 Pending Leaves: %d  |  On Leave Today: %d",
                    d.getTotalEmployees(),
                    d.getActiveEmployees(), d.getInactiveEmployees(),
                    d.getTotalAdmins(), d.getTotalManagers(), d.getTotalRegularEmployees(),
                    d.getTotalDepartments(), d.getTotalDesignations(),
                    d.getPendingLeaves(), d.getApprovedLeavesToday());
            return AIChatResponse.builder()
                    .reply(summary).action("GET_ADMIN_DASHBOARD").actionPerformed(true)
                    .quickReplies(List.of("All Leaves", "Team Attendance", "Announcements", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("Error: " + e.getMessage(), getDefaultQuickReplies(role));
        }
    }

    private AIChatResponse getAllLeaves(String email, Role role) {
        try {
            Page<LeaveApplication> leaves = leaveService.getAllLeaveApplications(null,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "appliedDate")));
            if (leaves.isEmpty()) {
                return reply("No leave applications in the system.", getDefaultQuickReplies(role));
            }
            StringBuilder sb = new StringBuilder("📋 All Leave Applications (latest 10):\n\n");
            for (LeaveApplication la : leaves.getContent()) {
                String statusIcon = switch (la.getStatus()) {
                    case PENDING -> "🟡";
                    case APPROVED -> "🟢";
                    case REJECTED -> "🔴";
                    case CANCELLED -> "⚪";
                };
                sb.append(String.format("%s #%d — %s %s | %s | %s to %s | %s\n",
                        statusIcon, la.getLeaveId(),
                        la.getEmployee().getFirstName(), la.getEmployee().getLastName(),
                        la.getLeaveType().getLeaveTypeName(),
                        formatDate(la.getStartDate()), formatDate(la.getEndDate()),
                        la.getStatus()));
            }
            return AIChatResponse.builder()
                    .reply(sb.toString()).action("GET_ALL_LEAVES").actionPerformed(true)
                    .quickReplies(List.of("Approve Leave", "Reject Leave", "Admin Dashboard", "Help"))
                    .build();
        } catch (Exception e) {
            return reply("Error: " + e.getMessage(), getDefaultQuickReplies(role));
        }
    }

    // ═══════════════════════════════════════════
    //  GENERAL CHAT (Ollama fallback)
    // ═══════════════════════════════════════════

    private AIChatResponse handleGeneralChat(AIChatRequest request, Role userRole) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a helpful, concise HR assistant for WorkForce HRMS. ");
            prompt.append("The user's role is: ").append(userRole.name()).append(". ");
            prompt.append("Keep responses short (2-3 sentences max). ");
            prompt.append("If the user wants to perform an action, tell them to say it directly. Do NOT use markdown.\n\n");

            if (request.getHistory() != null) {
                for (AIChatRequest.ChatHistoryEntry entry : request.getHistory()) {
                    prompt.append(entry.getRole().equals("user") ? "User: " : "Assistant: ");
                    prompt.append(entry.getContent()).append("\n");
                }
            }
            prompt.append("User: ").append(request.getMessage()).append("\nAssistant:");

            String llmReply = ollamaClient.generate(prompt.toString());
            if (llmReply != null && !llmReply.isBlank() && !llmReply.startsWith("Error")) {
                return reply(llmReply.trim(), getDefaultQuickReplies(userRole));
            }
        } catch (Exception ignored) {}

        return reply("I'm not sure I understand that. Type 'help' to see what I can do for you.",
                getDefaultQuickReplies(userRole));
    }

    // ═══════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════

    private AIChatResponse reply(String message, String... quickReplies) {
        return AIChatResponse.builder()
                .reply(message)
                .actionPerformed(false)
                .quickReplies(quickReplies != null && quickReplies.length > 0
                        ? List.of(quickReplies) : null)
                .build();
    }

    private boolean isAbortCommand(String msg) {
        return containsAny(msg, "abort", "stop", "nevermind", "never mind",
                "forget it", "quit", "exit flow", "start over");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String progressBar(int used, int total) {
        if (total == 0) return "";
        int filled = (int) Math.round((double) used / total * 5);
        return "▓".repeat(Math.min(filled, 5)) + "░".repeat(Math.max(5 - filled, 0));
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    private List<String> getLeaveTypeNames() {
        try {
            return leaveService.getAllLeaveType().stream()
                    .filter(LeaveType::getIsActive)
                    .map(LeaveType::getLeaveTypeName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of("Sick Leave", "Casual Leave", "Earned Leave");
        }
    }

    // ═══════════════════════════════════════════
    //  LEAVE TYPE DETECTION
    // ═══════════════════════════════════════════

    private Integer detectLeaveTypeFromMessage(String lower) {
        try {
            List<LeaveType> types = leaveService.getAllLeaveType();
            for (LeaveType t : types) {
                String name = t.getLeaveTypeName().toLowerCase();
                // Check for name parts in message
                String[] words = name.split("\\s+");
                for (String word : words) {
                    if (word.length() > 3 && lower.contains(word)) {
                        return t.getLeaveTypeId();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback keyword matching
        if (lower.contains("sick")) return findLeaveTypeId("sick");
        if (lower.contains("casual")) return findLeaveTypeId("casual");
        if (lower.contains("earned")) return findLeaveTypeId("earned");
        if (lower.contains("comp")) return findLeaveTypeId("comp");
        if (lower.contains("lop") || lower.contains("loss of pay")) return findLeaveTypeId("loss");
        return null;
    }

    private Integer detectLeaveTypeFromInput(String lower) {
        try {
            List<LeaveType> types = leaveService.getAllLeaveType();
            for (LeaveType t : types) {
                if (lower.contains(t.getLeaveTypeName().toLowerCase())) {
                    return t.getLeaveTypeId();
                }
            }
            // Partial match
            for (LeaveType t : types) {
                String name = t.getLeaveTypeName().toLowerCase();
                for (String word : name.split("\\s+")) {
                    if (word.length() > 3 && lower.contains(word)) {
                        return t.getLeaveTypeId();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Number input
        try {
            int id = Integer.parseInt(lower.replaceAll("[^0-9]", ""));
            return id > 0 ? id : null;
        } catch (Exception ignored) {}

        return null;
    }

    private Integer findLeaveTypeId(String keyword) {
        try {
            return leaveService.getAllLeaveType().stream()
                    .filter(t -> t.getLeaveTypeName().toLowerCase().contains(keyword))
                    .findFirst().map(LeaveType::getLeaveTypeId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  NATURAL DATE PARSING
    // ═══════════════════════════════════════════

    private LocalDate parseNaturalDate(String input) {
        if (input == null || input.isBlank()) return null;
        String text = input.trim().toLowerCase()
                .replaceAll("[,.]", " ").replaceAll("\\s+", " ").trim();

        LocalDate today = LocalDate.now();

        // Relative dates
        if (text.equals("today")) return today;
        if (text.equals("tomorrow") || text.equals("tmrw") || text.equals("tmr")) return today.plusDays(1);
        if (text.equals("day after tomorrow")) return today.plusDays(2);

        // "next monday", "this friday", etc.
        if (text.startsWith("next ") || text.startsWith("this ")) {
            String dayName = text.substring(5).trim();
            DayOfWeek dow = parseDayOfWeek(dayName);
            if (dow != null) {
                return text.startsWith("next ")
                        ? today.with(TemporalAdjusters.next(dow))
                        : today.with(TemporalAdjusters.nextOrSame(dow));
            }
        }

        // ISO format: 2026-03-11
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {}

        // "March 11", "March 11 2026", "Mar 11", "Mar 11 2026"
        Pattern p1 = Pattern.compile("(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:\\s+(\\d{4}))?");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            int month = parseMonth(m1.group(1));
            int day = Integer.parseInt(m1.group(2));
            int year = m1.group(3) != null ? Integer.parseInt(m1.group(3)) : today.getYear();
            try {
                LocalDate parsed = LocalDate.of(year, month, day);
                // If date is in the past this year, try next year
                if (parsed.isBefore(today) && m1.group(3) == null) {
                    parsed = LocalDate.of(year + 1, month, day);
                }
                return parsed;
            } catch (Exception ignored) {}
        }

        // "11 March", "11 March 2026"
        Pattern p2 = Pattern.compile("(\\d{1,2})\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\\s+(\\d{4}))?");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            int day = Integer.parseInt(m2.group(1));
            int month = parseMonth(m2.group(2));
            int year = m2.group(3) != null ? Integer.parseInt(m2.group(3)) : today.getYear();
            try {
                LocalDate parsed = LocalDate.of(year, month, day);
                if (parsed.isBefore(today) && m2.group(3) == null) {
                    parsed = LocalDate.of(year + 1, month, day);
                }
                return parsed;
            } catch (Exception ignored) {}
        }

        // dd/MM/yyyy or dd-MM-yyyy
        Pattern p3 = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})(?:[/\\-](\\d{2,4}))?");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            int a = Integer.parseInt(m3.group(1));
            int b = Integer.parseInt(m3.group(2));
            int year = m3.group(3) != null ? Integer.parseInt(m3.group(3)) : today.getYear();
            if (year < 100) year += 2000;

            // Try dd/MM first
            try {
                return LocalDate.of(year, b, a);
            } catch (Exception ignored) {}
            // Try MM/dd
            try {
                return LocalDate.of(year, a, b);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private List<LocalDate> extractDates(String message) {
        List<LocalDate> dates = new ArrayList<>();
        String text = message.toLowerCase().replaceAll("[,.]", " ");

        // Try to find all date-like patterns
        // "March 11", "March 12", etc.
        Pattern monthDay = Pattern.compile("(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:\\s+(\\d{4}))?");
        Matcher m = monthDay.matcher(text);
        while (m.find()) {
            int month = parseMonth(m.group(1));
            int day = Integer.parseInt(m.group(2));
            int year = m.group(3) != null ? Integer.parseInt(m.group(3)) : LocalDate.now().getYear();
            try { dates.add(LocalDate.of(year, month, day)); } catch (Exception ignored) {}
        }

        // ISO dates: 2026-03-11
        Pattern iso = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
        Matcher mi = iso.matcher(message);
        while (mi.find()) {
            try { dates.add(LocalDate.parse(mi.group())); } catch (Exception ignored) {}
        }

        // Relative dates in text
        if (text.contains("tomorrow")) dates.add(LocalDate.now().plusDays(1));

        return dates;
    }

    private String extractReason(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("reason:")) {
            return message.substring(lower.indexOf("reason:") + 7).trim();
        }
        if (lower.contains("because ")) {
            return message.substring(lower.indexOf("because ") + 8).trim();
        }
        if (lower.contains(" for ") && lower.indexOf(" for ") > lower.length() / 2) {
            String after = message.substring(lower.lastIndexOf(" for ") + 5).trim();
            if (after.length() > 5 && !after.toLowerCase().matches("\\d+ day.*|a day.*|leave.*|me.*")) {
                return after;
            }
        }
        return null;
    }

    private int parseMonth(String name) {
        return switch (name.substring(0, 3)) {
            case "jan" -> 1; case "feb" -> 2; case "mar" -> 3; case "apr" -> 4;
            case "may" -> 5; case "jun" -> 6; case "jul" -> 7; case "aug" -> 8;
            case "sep" -> 9; case "oct" -> 10; case "nov" -> 11; case "dec" -> 12;
            default -> 1;
        };
    }

    private DayOfWeek parseDayOfWeek(String name) {
        return switch (name.toLowerCase().trim()) {
            case "monday", "mon" -> DayOfWeek.MONDAY;
            case "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY;
            case "wednesday", "wed" -> DayOfWeek.WEDNESDAY;
            case "thursday", "thu", "thurs" -> DayOfWeek.THURSDAY;
            case "friday", "fri" -> DayOfWeek.FRIDAY;
            case "saturday", "sat" -> DayOfWeek.SATURDAY;
            case "sunday", "sun" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
