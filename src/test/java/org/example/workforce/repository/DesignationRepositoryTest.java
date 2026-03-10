package org.example.workforce.repository;

import org.example.workforce.model.Designation;
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
class DesignationRepositoryTest {

    @Autowired
    private DesignationRepository designationRepository;

    private Designation designation;

    @BeforeEach
    void setUp() {
        designation = Designation.builder()
                .designationName("Software Engineer")
                .isActive(true)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Designation saved = designationRepository.save(designation);
        Optional<Designation> found = designationRepository.findById(saved.getDesignationId());
        assertTrue(found.isPresent());
        assertEquals("Software Engineer", found.get().getDesignationName());
    }

    @Test
    void testFindByDesignationName() {
        designationRepository.save(designation);
        Optional<Designation> found = designationRepository.findByDesignationName("Software Engineer");
        assertTrue(found.isPresent());
    }

    @Test
    void testExistsByDesignationName() {
        designationRepository.save(designation);
        assertTrue(designationRepository.existsByDesignationName("Software Engineer"));
        assertFalse(designationRepository.existsByDesignationName("Manager"));
    }

    @Test
    void testUpdateDesignation() {
        Designation saved = designationRepository.save(designation);
        saved.setDesignationName("Senior Engineer");
        Designation updated = designationRepository.save(saved);
        assertEquals("Senior Engineer", updated.getDesignationName());
    }

    @Test
    void testDeleteDesignation() {
        Designation saved = designationRepository.save(designation);
        designationRepository.delete(saved);
        assertFalse(designationRepository.existsByDesignationName("Software Engineer"));
    }
}

