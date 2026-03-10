package org.example.workforce.repository;

import org.example.workforce.model.Department;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DepartmentRepositoryTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .departmentName("IT")
                .isActive(true)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Department saved = departmentRepository.save(department);
        Optional<Department> found = departmentRepository.findById(saved.getDepartmentId());
        assertTrue(found.isPresent());
        assertEquals("IT", found.get().getDepartmentName());
    }

    @Test
    void testFindByDepartmentName() {
        departmentRepository.save(department);
        Optional<Department> found = departmentRepository.findByDepartmentName("IT");
        assertTrue(found.isPresent());
        assertEquals("IT", found.get().getDepartmentName());
    }

    @Test
    void testExistsByDepartmentName() {
        departmentRepository.save(department);
        assertTrue(departmentRepository.existsByDepartmentName("IT"));
        assertFalse(departmentRepository.existsByDepartmentName("HR"));
    }

    @Test
    void testUpdateDepartment() {
        Department saved = departmentRepository.save(department);
        saved.setDepartmentName("Updated IT");
        Department updated = departmentRepository.save(saved);
        assertEquals("Updated IT", updated.getDepartmentName());
    }

    @Test
    void testDeleteDepartment() {
        Department saved = departmentRepository.save(department);
        departmentRepository.delete(saved);
        assertFalse(departmentRepository.existsByDepartmentName("IT"));
    }
}

