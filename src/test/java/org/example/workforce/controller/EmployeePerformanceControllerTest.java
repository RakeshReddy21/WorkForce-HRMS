package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.GoalRequest;
import org.example.workforce.dto.PerformanceReviewRequest;
import org.example.workforce.model.Goal;
import org.example.workforce.model.PerformanceReview;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeePerformanceController.class)
class EmployeePerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PerformanceService performanceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testCreateReview() throws Exception {
        PerformanceReview review = PerformanceReview.builder().reviewId(1).build();
        when(performanceService.createReview(eq("emp@test.com"), any(PerformanceReviewRequest.class))).thenReturn(review);

        PerformanceReviewRequest request = new PerformanceReviewRequest();
        request.setReviewPeriod("Q1 2026");

        mockMvc.perform(post("/api/employees/reviews").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyReviews() throws Exception {
        Page<PerformanceReview> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getMyReviews(eq("emp@test.com"), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetReviewById() throws Exception {
        PerformanceReview review = PerformanceReview.builder().reviewId(1).build();
        when(performanceService.getReviewById("emp@test.com", 1)).thenReturn(review);

        mockMvc.perform(get("/api/employees/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testSubmitReview() throws Exception {
        PerformanceReview review = PerformanceReview.builder().reviewId(1).build();
        when(performanceService.submitReview("emp@test.com", 1)).thenReturn(review);

        mockMvc.perform(patch("/api/employees/reviews/1/submit").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testCreateGoal() throws Exception {
        Goal goal = Goal.builder().goalId(1).title("Test Goal").build();
        when(performanceService.createGoal(eq("emp@test.com"), any(GoalRequest.class))).thenReturn(goal);

        GoalRequest request = new GoalRequest();
        request.setTitle("Test Goal");
        request.setDeadline(java.time.LocalDate.of(2026, 12, 31));
        request.setPriority("HIGH");

        mockMvc.perform(post("/api/employees/goals").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyGoals() throws Exception {
        Page<Goal> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getMyGoals(eq("emp@test.com"), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

