package org.example.workforce.repository;

import org.example.workforce.model.Holiday;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HolidayRepositoryTest {

    @Autowired
    private HolidayRepository holidayRepository;

    private Holiday holiday;

    @BeforeEach
    void setUp() {
        holiday = Holiday.builder()
                .holidayName("New Year")
                .holidayDate(LocalDate.of(2024, 1, 1))
                .year(2024)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Holiday saved = holidayRepository.save(holiday);
        assertNotNull(saved.getHolidayId());
        assertEquals("New Year", saved.getHolidayName());
    }

    @Test
    void testFindAll() {
        holidayRepository.save(holiday);
        assertFalse(holidayRepository.findAll().isEmpty());
    }
}

