package org.example.workforce.repository;

import org.example.workforce.model.OfficeLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OfficeLocationRepositoryTest {

    @Autowired
    private OfficeLocationRepository officeLocationRepository;

    private OfficeLocation officeLocation;

    @BeforeEach
    void setUp() {
        officeLocation = OfficeLocation.builder()
                .locationName("Main Office")
                .address("123 Main St")
                .latitude(40.7128)
                .longitude(-74.0060)
                .radiusMeters(100)
                .isActive(true)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        OfficeLocation saved = officeLocationRepository.save(officeLocation);
        assertNotNull(saved.getLocationId());
        assertEquals("Main Office", saved.getLocationName());
    }

    @Test
    void testFindAll() {
        officeLocationRepository.save(officeLocation);
        assertFalse(officeLocationRepository.findAll().isEmpty());
    }
}

