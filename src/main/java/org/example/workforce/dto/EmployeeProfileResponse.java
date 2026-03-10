package org.example.workforce.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeProfileResponse {
    private Integer employeeId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Integer departmentId;
    private String departmentName;
    private Integer designationId;
    private String designationTitle;
    private LocalDate joiningDate;
    private BigDecimal salary;
    private String role;
    private Boolean isActive;
    private Boolean twoFactorEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ManagerInfo manager;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ManagerInfo {
        private Integer managerId;
        private String managerCode;
        private String managerName;
        private String managerEmail;
        private String managerPhone;
    }
}
