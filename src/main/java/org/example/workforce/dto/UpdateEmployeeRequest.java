package org.example.workforce.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeRequest {
    @Size(max = 100)
    private String firstName;
    @Size(max = 100)
    private String lastName;
    @Email(message = "Invalid email format")
    private String email;
    @Size(max = 20)
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    @Size(max = 100)
    private String emergencyContactName;
    @Size(max = 20)
    private String emergencyContactPhone;
    private Integer departmentId;
    private Integer designationId;
    private LocalDate joiningDate;
    private BigDecimal salary;
    private String role;
}
