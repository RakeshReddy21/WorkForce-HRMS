package org.example.workforce.config;
import org.example.workforce.model.Department;
import org.example.workforce.model.Designation;
import org.example.workforce.model.Employee;
import org.example.workforce.model.LeaveType;
import org.example.workforce.model.enums.Gender;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.DepartmentRepository;
import org.example.workforce.repository.DesignationRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.repository.LeaveTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner initDatabase(DepartmentRepository departmentRepository,
            DesignationRepository designationRepository, LeaveTypeRepository leaveTypeRepository) {
        return args -> {
            if (departmentRepository.count() == 0) {
                List<Department> departments = Arrays.asList(
                        Department.builder().departmentName("IT").description("Information Technology").build(),
                        Department.builder().departmentName("HR").description("Human Resources").build(),
                        Department.builder().departmentName("Finance").description("Financial Department").build(),
                        Department.builder().departmentName("Marketing").description("Marketing Department").build());
                departmentRepository.saveAll(departments);
                System.out.println("Seeded Departments");
            }
            if (designationRepository.count() == 0) {
                List<Designation> designations = Arrays.asList(
                        Designation.builder().designationName("Software Engineer").description("Develops software")
                                .build(),
                        Designation.builder().designationName("HR Manager").description("Manages HR").build(),
                        Designation.builder().designationName("Accountant").description("Manages Accounts").build(),
                        Designation.builder().designationName("Marketing Executive").description("Marketing strategies")
                                .build());
                designationRepository.saveAll(designations);
                System.out.println("Seeded Designations");
            }

            if (leaveTypeRepository.count() == 0) {
                List<LeaveType> leaveTypes = Arrays.asList(
                        LeaveType.builder()
                                .leaveTypeName("Earned Leave")
                                .description("Earned/Privilege leave accrued over time. Can be carried forward.")
                                .defaultDays(15)
                                .isPaidLeave(true)
                                .isCarryForwardEnabled(true)
                                .maxCarryForwardDays(5)
                                .isLossOfPay(false)
                                .build(),
                        LeaveType.builder()
                                .leaveTypeName("Sick Leave")
                                .description("Leave for medical/health reasons. May require medical certificate for extended period.")
                                .defaultDays(12)
                                .isPaidLeave(true)
                                .isCarryForwardEnabled(false)
                                .maxCarryForwardDays(0)
                                .isLossOfPay(false)
                                .build(),
                        LeaveType.builder()
                                .leaveTypeName("Casual Leave")
                                .description("Short-duration leave for personal/urgent matters.")
                                .defaultDays(10)
                                .isPaidLeave(true)
                                .isCarryForwardEnabled(false)
                                .maxCarryForwardDays(0)
                                .isLossOfPay(false)
                                .build(),
                        LeaveType.builder()
                                .leaveTypeName("Loss of Pay")
                                .description("Unpaid leave when all other leave balances are exhausted.")
                                .defaultDays(0)
                                .isPaidLeave(false)
                                .isCarryForwardEnabled(false)
                                .maxCarryForwardDays(0)
                                .isLossOfPay(true)
                                .build()
                );
                leaveTypeRepository.saveAll(leaveTypes);
                System.out.println("Seeded Default Leave Types: Earned Leave, Sick Leave, Casual Leave, Loss of Pay");
            }
        };
    }

    @Bean
    CommandLineRunner initAdmin(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!employeeRepository.existsByRole(Role.ADMIN)) {
                Employee admin = Employee.builder()
                        .employeeCode("ADM001")
                        .firstName("System")
                        .lastName("Admin")
                        .email("admin@workforce.com")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
                        .phone("0000000000")
                        .dateOfBirth(LocalDate.of(1990, 1, 1))
                        .gender(Gender.MALE)
                        .address("WorkForce HQ")
                        .joiningDate(LocalDate.now())
                        .salary(BigDecimal.ZERO)
                        .role(Role.ADMIN)
                        .isActive(true)
                        .build();
                employeeRepository.save(admin);
                System.out.println("=== Default Admin Seeded ===");
                System.out.println("Email   : admin@workforce.com");
                System.out.println("Password: Admin@123");
                System.out.println("Code    : ADM001");
                System.out.println("============================");
            }
        };
    }
}
