package org.example.workforce.config;
import org.example.workforce.model.Department;
import org.example.workforce.model.Designation;
import org.example.workforce.repository.DepartmentRepository;
import org.example.workforce.repository.DesignationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner initDatabase(DepartmentRepository departmentRepository,
            DesignationRepository designationRepository) {
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
        };
    }
}
