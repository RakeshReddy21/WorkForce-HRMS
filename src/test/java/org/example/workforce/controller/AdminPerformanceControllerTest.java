package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ManagerFeedbackRequest;
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

@WebMvcTest(AdminPerformanceController.class)
class AdminPerformanceControllerTest {

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
    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        review = PerformanceReview.builder().reviewId(1).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllReviews() throws Exception {
        Page<PerformanceReview> page = new PageImpl<>(Collections.emptyList());
        when(performanceService.getAllReviews(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/performance/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetReviewById() throws Exception {
        when(performanceService.getAdminReviewById(1)).thenReturn(review);

        mockMvc.perform(get("/api/admin/performance/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testProvideReviewFeedback() throws Exception {
        when(performanceService.provideAdminReviewFeedback(anyString(), eq(1), any(ManagerFeedbackRequest.class))).thenReturn(review);

        ManagerFeedbackRequest request = new ManagerFeedbackRequest();
        request.setManagerFeedback("Good work");
        request.setManagerRating(4);

        mockMvc.perform(patch("/api/admin/performance/reviews/1/feedback").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

