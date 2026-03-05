package org.example.workforce.service;

import org.example.workforce.exception.BadRequestException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.OtpVerification;
import org.example.workforce.repository.OtpVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    public String generateAndSendOtp(Employee employee) {

        otpRepository.invalidateAllOtpsForEmployee(employee.getEmployeeId());

        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        String preAuthToken = UUID.randomUUID().toString();

        OtpVerification otpRecord = OtpVerification.builder()
                .employee(employee)
                .otp(otp)
                .preAuthToken(preAuthToken)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .attempts(0)
                .isUsed(false)
                .build();
        otpRepository.save(otpRecord);

        String employeeName = employee.getFirstName() + " " + employee.getLastName();
        emailService.sendOtpEmail(employee.getEmail(), employeeName, otp);

        log.info("OTP generated and email sent for employee: {} (ID: {})", employee.getEmail(), employee.getEmployeeId());
        return preAuthToken;
    }

    public Employee verifyOtp(String preAuthToken, String otp) {
        OtpVerification otpRecord = otpRepository.findByPreAuthTokenAndIsUsedFalse(preAuthToken)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification session. Please login again."));

        if (otpRecord.isExpired()) {
            otpRecord.setIsUsed(true);
            otpRepository.save(otpRecord);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        if (otpRecord.getAttempts() >= maxAttempts) {
            otpRecord.setIsUsed(true);
            otpRepository.save(otpRecord);
            throw new BadRequestException("Too many incorrect attempts. Please login again.");
        }

        otpRecord.setAttempts(otpRecord.getAttempts() + 1);

        if (!otpRecord.getOtp().equals(otp.trim())) {
            otpRepository.save(otpRecord);
            int remaining = maxAttempts - otpRecord.getAttempts();
            throw new BadRequestException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        otpRecord.setIsUsed(true);
        otpRepository.save(otpRecord);

        log.info("OTP verified successfully for employee: {}", otpRecord.getEmployee().getEmail());
        return otpRecord.getEmployee();
    }

    public String resendOtp(String preAuthToken) {
        OtpVerification otpRecord = otpRepository.findByPreAuthTokenAndIsUsedFalse(preAuthToken)
                .orElseThrow(() -> new BadRequestException("Invalid verification session. Please login again."));

        Employee employee = otpRecord.getEmployee();

        return generateAndSendOtp(employee);
    }

    public void cleanupExpiredOtps() {
        otpRepository.deleteExpiredOtps(LocalDateTime.now());
        log.debug("Cleaned up expired OTP records");
    }
}
