package org.example.workforce.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@workforce.com");
        ReflectionTestUtils.setField(emailService, "otpExpiryMinutes", 5);
    }

    @Test
    void sendOtpEmail_Success() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail("employee@test.com", "John Doe", "123456");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendOtpEmail_VerifiesRecipient() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail("jane@test.com", "Jane Smith", "654321");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendOtpEmail_MessagingException_ThrowsRuntimeException() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail server unavailable"));

        assertThrows(RuntimeException.class,
                () -> emailService.sendOtpEmail("user@test.com", "Test User", "111111"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendOtpEmail_SendFails_ThrowsRuntimeException() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class,
                () -> emailService.sendOtpEmail("user@test.com", "Test User", "999999"));
    }

    @Test
    void sendOtpEmail_WithDifferentOtpLengths() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail("user@test.com", "User", "123456");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendOtpEmail_WithSpecialCharactersInName() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail("user@test.com", "O'Brien-Smith", "123456");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendOtpEmail_CalledMultipleTimes() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail("user1@test.com", "User1", "111111");
        emailService.sendOtpEmail("user2@test.com", "User2", "222222");
        emailService.sendOtpEmail("user3@test.com", "User3", "333333");

        verify(mailSender, times(3)).createMimeMessage();
        verify(mailSender, times(3)).send(mimeMessage);
    }
}
