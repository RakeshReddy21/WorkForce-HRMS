package org.example.workforce.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Async
    public void sendOtpEmail(String toEmail, String employeeName, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("WorkForce HRMS — Your Login Verification Code");
            helper.setText(buildOtpEmailTemplate(employeeName, otp), true);

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String employeeName, String employeeCode,
                                  String password, String role) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to WorkForce HRMS — Your Account is Ready!");
            helper.setText(buildWelcomeEmailTemplate(employeeName, employeeCode, toEmail, password, role), true);

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildWelcomeEmailTemplate(String employeeName, String employeeCode,
                                              String email, String password, String role) {
        String roleBadgeColor;
        switch (role.toUpperCase()) {
            case "ADMIN":   roleBadgeColor = "#8b5cf6"; break;
            case "MANAGER": roleBadgeColor = "#3b82f6"; break;
            default:        roleBadgeColor = "#10b981"; break;
        }

        return "<!DOCTYPE html>" +
            "<html><head><meta charset=\"UTF-8\"></head>" +
            "<body style=\"margin:0;padding:0;background-color:#f8fafc;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f8fafc;padding:40px 0;\">" +
            "<tr><td align=\"center\">" +
            "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 6px rgba(0,0,0,0.07);\">" +

            // Header
            "<tr><td style=\"background:linear-gradient(135deg,#1e3a5f 0%,#2563eb 100%);padding:36px 40px;text-align:center;\">" +
            "<h1 style=\"margin:0;font-size:28px;font-weight:700;color:#ffffff;letter-spacing:0.5px;\">WorkForce</h1>" +
            "<p style=\"margin:4px 0 0;font-size:12px;color:#93c5fd;letter-spacing:2px;text-transform:uppercase;\">HRMS Portal</p>" +
            "</td></tr>" +

            // Welcome icon + greeting
            "<tr><td style=\"padding:40px 40px 0;text-align:center;\">" +
            "<div style=\"width:64px;height:64px;margin:0 auto 20px;border-radius:50%;background:linear-gradient(135deg,#dbeafe,#eff6ff);display:flex;align-items:center;justify-content:center;\">" +
            "<span style=\"font-size:32px;\">&#127881;</span>" +
            "</div>" +
            "<h2 style=\"margin:0 0 8px;font-size:22px;font-weight:700;color:#1e293b;\">Welcome aboard, " + employeeName + "!</h2>" +
            "<p style=\"margin:0 0 28px;font-size:14px;color:#64748b;line-height:1.6;\">" +
            "Your WorkForce HRMS account has been created. Below are your login credentials to get started.</p>" +
            "</td></tr>" +

            // Credentials card
            "<tr><td style=\"padding:0 40px;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;\">" +
            "<tr><td style=\"padding:20px 24px;border-bottom:1px solid #e2e8f0;\">" +
            "<table width=\"100%\"><tr>" +
            "<td style=\"font-size:13px;color:#64748b;font-weight:500;\">Employee Code</td>" +
            "<td style=\"font-size:14px;color:#1e293b;font-weight:700;text-align:right;font-family:monospace;letter-spacing:1px;\">" + employeeCode + "</td>" +
            "</tr></table></td></tr>" +
            "<tr><td style=\"padding:20px 24px;border-bottom:1px solid #e2e8f0;\">" +
            "<table width=\"100%\"><tr>" +
            "<td style=\"font-size:13px;color:#64748b;font-weight:500;\">Email</td>" +
            "<td style=\"font-size:14px;color:#1e293b;font-weight:600;text-align:right;\">" + email + "</td>" +
            "</tr></table></td></tr>" +
            "<tr><td style=\"padding:20px 24px;border-bottom:1px solid #e2e8f0;\">" +
            "<table width=\"100%\"><tr>" +
            "<td style=\"font-size:13px;color:#64748b;font-weight:500;\">Password</td>" +
            "<td style=\"font-size:14px;color:#1e293b;font-weight:700;text-align:right;font-family:monospace;letter-spacing:1px;\">" + password + "</td>" +
            "</tr></table></td></tr>" +
            "<tr><td style=\"padding:20px 24px;\">" +
            "<table width=\"100%\"><tr>" +
            "<td style=\"font-size:13px;color:#64748b;font-weight:500;\">Role</td>" +
            "<td style=\"text-align:right;\">" +
            "<span style=\"display:inline-block;padding:4px 14px;border-radius:20px;font-size:12px;font-weight:700;" +
            "color:#ffffff;background-color:" + roleBadgeColor + ";letter-spacing:0.5px;\">" + role.toUpperCase() + "</span>" +
            "</td></tr></table></td></tr>" +
            "</table></td></tr>" +

            // Security notice
            "<tr><td style=\"padding:24px 40px 0;\">" +
            "<div style=\"padding:16px 20px;background-color:#fef3c7;border-left:4px solid #f59e0b;border-radius:0 8px 8px 0;\">" +
            "<p style=\"margin:0;font-size:13px;color:#92400e;line-height:1.5;\">" +
            "<strong>&#128274; Security Tip:</strong> Please change your password after your first login to keep your account secure.</p>" +
            "</div></td></tr>" +

            // What you can do
            "<tr><td style=\"padding:24px 40px 0;\">" +
            "<p style=\"margin:0 0 12px;font-size:14px;font-weight:600;color:#1e293b;\">What you can do on WorkForce:</p>" +
            "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">" +
            "<tr><td style=\"padding:6px 0;font-size:13px;color:#475569;\">&#128197; &nbsp;Mark attendance &amp; track hours</td></tr>" +
            "<tr><td style=\"padding:6px 0;font-size:13px;color:#475569;\">&#128221; &nbsp;Apply for leaves &amp; view balance</td></tr>" +
            "<tr><td style=\"padding:6px 0;font-size:13px;color:#475569;\">&#127919; &nbsp;Set goals &amp; track performance</td></tr>" +
            "<tr><td style=\"padding:6px 0;font-size:13px;color:#475569;\">&#128276; &nbsp;Get real-time notifications</td></tr>" +
            "<tr><td style=\"padding:6px 0;font-size:13px;color:#475569;\">&#129302; &nbsp;Chat with AI assistant for quick help</td></tr>" +
            "</table></td></tr>" +

            // Footer
            "<tr><td style=\"padding:32px 40px;\">" +
            "<div style=\"border-top:1px solid #e2e8f0;padding-top:24px;text-align:center;\">" +
            "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">This is an automated message from WorkForce HRMS. Please do not reply.</p>" +
            "<p style=\"margin:8px 0 0;font-size:11px;color:#cbd5e1;\">&copy; 2026 WorkForce HRMS. All rights reserved.</p>" +
            "</div></td></tr>" +

            "</table></td></tr></table></body></html>";
    }

    private String buildOtpEmailTemplate(String employeeName, String otp) {

        StringBuilder otpBoxes = new StringBuilder();
        for (char digit : otp.toCharArray()) {
            otpBoxes.append(
                "<td style=\"width:48px;height:56px;text-align:center;font-size:28px;font-weight:700;" +
                "color:#1e293b;background-color:#f1f5f9;border:2px solid #e2e8f0;border-radius:12px;" +
                "font-family:'Segoe UI',Roboto,sans-serif;letter-spacing:2px;\">"
                + digit + "</td><td style=\"width:8px;\"></td>"
            );
        }

        return "<!DOCTYPE html>" +
            "<html><head><meta charset=\"UTF-8\"></head>" +
            "<body style=\"margin:0;padding:0;background-color:#f8fafc;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f8fafc;padding:40px 0;\">" +
            "<tr><td align=\"center\">" +
            "<table width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 6px rgba(0,0,0,0.07);\">" +

            "<tr><td style=\"background:linear-gradient(135deg,#1e3a5f 0%,#2563eb 100%);padding:32px 40px;text-align:center;\">" +
            "<h1 style=\"margin:0;font-size:24px;font-weight:700;color:#ffffff;letter-spacing:0.5px;\">WorkForce</h1>" +
            "<p style=\"margin:4px 0 0;font-size:12px;color:#93c5fd;letter-spacing:2px;text-transform:uppercase;\">HRMS Portal</p>" +
            "</td></tr>" +

            "<tr><td style=\"padding:40px;\">" +
            "<p style=\"margin:0 0 8px;font-size:16px;color:#1e293b;\">Hello <strong>" + employeeName + "</strong>,</p>" +
            "<p style=\"margin:0 0 28px;font-size:14px;color:#64748b;line-height:1.6;\">" +
            "We received a login request for your WorkForce account. Use the verification code below to complete your sign-in:</p>" +

            "<table cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 auto 28px;\">" +
            "<tr>" + otpBoxes.toString() + "</tr>" +
            "</table>" +

            "<div style=\"text-align:center;margin-bottom:28px;\">" +
            "<span style=\"display:inline-block;padding:8px 20px;background-color:#fef3c7;color:#92400e;border-radius:8px;font-size:13px;font-weight:500;\">" +
            "&#9200; This code expires in " + otpExpiryMinutes + " minutes" +
            "</span></div>" +

            "<div style=\"padding:16px 20px;background-color:#f0f9ff;border-left:4px solid #3b82f6;border-radius:0 8px 8px 0;margin-bottom:8px;\">" +
            "<p style=\"margin:0;font-size:13px;color:#1e40af;line-height:1.5;\">" +
            "<strong>Security Notice:</strong> If you did not request this code, please ignore this email and ensure your account credentials are secure.</p>" +
            "</div>" +
            "</td></tr>" +

            "<tr><td style=\"padding:24px 40px;background-color:#f8fafc;border-top:1px solid #e2e8f0;text-align:center;\">" +
            "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">This is an automated message from WorkForce HRMS. Please do not reply.</p>" +
            "<p style=\"margin:8px 0 0;font-size:11px;color:#cbd5e1;\">&copy; 2026 WorkForce HRMS. All rights reserved.</p>" +
            "</td></tr>" +

            "</table></td></tr></table></body></html>";
    }
}
