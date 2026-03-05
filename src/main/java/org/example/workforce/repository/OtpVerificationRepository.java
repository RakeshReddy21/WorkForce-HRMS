package org.example.workforce.repository;

import org.example.workforce.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Integer> {

    Optional<OtpVerification> findByPreAuthTokenAndIsUsedFalse(String preAuthToken);

    @Modifying
    @Query("UPDATE OtpVerification o SET o.isUsed = true WHERE o.employee.employeeId = :employeeId AND o.isUsed = false")
    void invalidateAllOtpsForEmployee(@Param("employeeId") Integer employeeId);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);
}
