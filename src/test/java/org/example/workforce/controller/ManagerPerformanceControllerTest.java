package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ManagerFeedbackRequest;
import org.example.workforce.dto.ManagerGoalCommentRequest;
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

@WebMvcTest(ManagerPerformanceController.class)
class ManagerPerformanceControllerTest {

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
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamReviews() throws Exception {
        Page<PerformanceReview> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getTeamReviews(eq("mgr@test.com"), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/manager/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamReviewById() throws Exception {
        PerformanceReview review = PerformanceReview.builder().reviewId(1).build();
        when(performanceService.getTeamReviewById("mgr@test.com", 1)).thenReturn(review);

        mockMvc.perform(get("/api/manager/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testProvideReviewFeedback() throws Exception {
        PerformanceReview review = PerformanceReview.builder().reviewId(1).build();
        when(performanceService.provideReviewFeedback(eq("mgr@test.com"), eq(1), any(ManagerFeedbackRequest.class))).thenReturn(review);

        ManagerFeedbackRequest request = new ManagerFeedbackRequest();
        request.setManagerFeedback("Great job");
        request.setManagerRating(5);

        mockMvc.perform(patch("/api/manager/reviews/1/feedback").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetAllTeamGoals() throws Exception {
        Page<Goal> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getAllTeamGoals(eq("mgr@test.com"), any())).thenReturn(page);

        mockMvc.perform(get("/api/manager/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamMemberGoals() throws Exception {
        Page<Goal> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getTeamMemberGoals(eq("mgr@test.com"), eq("EMP001"), any())).thenReturn(page);

        mockMvc.perform(get("/api/manager/goals/EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testCommentOnGoal() throws Exception {
        Goal goal = Goal.builder().goalId(1).build();
        when(performanceService.commentOnGoal(eq("mgr@test.com"), eq(1), any(ManagerGoalCommentRequest.class))).thenReturn(goal);

        ManagerGoalCommentRequest request = new ManagerGoalCommentRequest();
        request.setManagerComments("Keep going");

        mockMvc.perform(patch("/api/manager/goals/1/comment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

