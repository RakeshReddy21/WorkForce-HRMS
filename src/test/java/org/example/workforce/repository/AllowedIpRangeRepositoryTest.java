package org.example.workforce.repository;

import org.example.workforce.model.AllowedIpRange;
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
class AllowedIpRangeRepositoryTest {

    @Autowired
    private AllowedIpRangeRepository allowedIpRangeRepository;

    private AllowedIpRange ipRange;

    @BeforeEach
    void setUp() {
        ipRange = AllowedIpRange.builder()
                .ipRange("192.168.1.1-192.168.1.255")
                .description("Office Network")
                .isActive(true)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        AllowedIpRange saved = allowedIpRangeRepository.save(ipRange);
        assertNotNull(saved.getIpRangeId());
        assertEquals("Office Network", saved.getDescription());
    }

    @Test
    void testFindAll() {
        allowedIpRangeRepository.save(ipRange);
        assertFalse(allowedIpRangeRepository.findAll().isEmpty());
    }
}

