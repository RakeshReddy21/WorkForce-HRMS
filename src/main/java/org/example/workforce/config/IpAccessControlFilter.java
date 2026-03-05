package org.example.workforce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.util.NetworkIpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class IpAccessControlFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpAccessControlFilter.class);

    @Autowired
    private IpAccessControlService ipAccessControlService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String clientIp = NetworkIpUtil.resolveClientIp(request);

        boolean isEmployeeEndpoint = requestUri.startsWith("/api/employees");
        boolean isManagerEndpoint = requestUri.startsWith("/api/manager");

        if (isEmployeeEndpoint || isManagerEndpoint) {

            log.info("[IP-FILTER] Checking IP for URI: {} | Client IP: {}", requestUri, clientIp);

            boolean allowed = ipAccessControlService.isIpAllowed(clientIp);

            if (!allowed) {
                log.warn("[IP-FILTER] BLOCKED — IP: {} | URI: {}", clientIp, requestUri);
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"Access denied. Your IP address ("
                                + clientIp + ") is not whitelisted. Please contact your administrator.\"}"
                );
                return;
            }

            log.info("[IP-FILTER] ALLOWED — IP: {} | URI: {}", clientIp, requestUri);
        }

        filterChain.doFilter(request, response);
    }
}
