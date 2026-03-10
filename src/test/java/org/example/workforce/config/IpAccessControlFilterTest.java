package org.example.workforce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpAccessControlFilterTest {

    @Mock
    private IpAccessControlService ipAccessControlService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private IpAccessControlFilter ipAccessControlFilter;

    @Test
    void testFilter_AllowedIp_EmployeeEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/employees/me");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(ipAccessControlService.isIpAllowed("192.168.1.100")).thenReturn(true);

        ipAccessControlFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testFilter_BlockedIp_EmployeeEndpoint() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        when(request.getRequestURI()).thenReturn("/api/employees/me");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(ipAccessControlService.isIpAllowed("10.0.0.1")).thenReturn(false);
        when(response.getWriter()).thenReturn(pw);

        ipAccessControlFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testFilter_AllowedIp_ManagerEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/manager/team");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(ipAccessControlService.isIpAllowed("192.168.1.100")).thenReturn(true);

        ipAccessControlFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testFilter_NonProtectedEndpoint_SkipsCheck() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        ipAccessControlFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(ipAccessControlService, never()).isIpAllowed(anyString());
    }

    @Test
    void testFilter_AdminEndpoint_SkipsCheck() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/admin/dashboard");

        ipAccessControlFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(ipAccessControlService, never()).isIpAllowed(anyString());
    }
}





