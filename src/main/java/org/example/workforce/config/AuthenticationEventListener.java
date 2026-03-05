package org.example.workforce.config;

import org.example.workforce.model.ActivityLog;
import org.example.workforce.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventListener {
    @Autowired
    private ActivityLogRepository activityLogRepository;

    @EventListener
    public void onAuthenticationFailureBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String email = (String) event.getAuthentication().getPrincipal();
        activityLogRepository.save(ActivityLog.builder()
                .action("LOGIN_FAILED")
                .entityType("AUTH")
                .details("Failed login attempt for email: " + email + " - Bad credentials")
                .status("FAILED")
                .build());
    }

    @EventListener
    public void onAuthenticationFailureDisabled(AuthenticationFailureDisabledEvent event) {
        String email = (String) event.getAuthentication().getPrincipal();
        activityLogRepository.save(ActivityLog.builder()
                .action("LOGIN_FAILED_DISABLED")
                .entityType("AUTH")
                .details("Login attempt for deactivated account: " + email)
                .status("FAILED")
                .build());
    }
}
