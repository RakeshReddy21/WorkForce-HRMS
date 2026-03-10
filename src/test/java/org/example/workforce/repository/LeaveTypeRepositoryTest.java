package org.example.workforce.repository;

import org.example.workforce.model.LeaveType;
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
class LeaveTypeRepositoryTest {

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    private LeaveType leaveType;

    @BeforeEach
    void setUp() {
        leaveType = LeaveType.builder()
                .leaveTypeName("Annual Leave")
                .defaultDays(20)
                .isActive(true)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        LeaveType saved = leaveTypeRepository.save(leaveType);
        assertNotNull(saved.getLeaveTypeId());
        assertEquals("Annual Leave", saved.getLeaveTypeName());
    }

    @Test
    void testFindByLeaveTypeName() {
        leaveTypeRepository.save(leaveType);
        assertTrue(leaveTypeRepository.findByLeaveTypeName("Annual Leave").isPresent());
    }

    @Test
    void testExistsByLeaveTypeName() {
        leaveTypeRepository.save(leaveType);
        assertTrue(leaveTypeRepository.existsByLeaveTypeName("Annual Leave"));
    }
}

