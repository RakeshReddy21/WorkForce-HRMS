package org.example.workforce.config;
import org.example.workforce.model.Department;
import org.example.workforce.model.Designation;
import org.example.workforce.repository.DepartmentRepository;
import org.example.workforce.repository.DesignationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataSeederTest {
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DesignationRepository designationRepository;
    private DataSeeder dataSeeder = new DataSeeder();
    @Test
    void testInitDatabase_SeedsDataWhenEmpty() throws Exception {
        when(departmentRepository.count()).thenReturn(0L);
        when(designationRepository.count()).thenReturn(0L);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository);
        runner.run();
        verify(departmentRepository, times(1)).saveAll(anyList());
        verify(designationRepository, times(1)).saveAll(anyList());
    }
    @Test
    void testInitDatabase_DoesNotSeedDataWhenNotEmpty() throws Exception {
        when(departmentRepository.count()).thenReturn(5L);
        when(designationRepository.count()).thenReturn(5L);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository);
        runner.run();
        verify(departmentRepository, never()).saveAll(anyList());
        verify(designationRepository, never()).saveAll(anyList());
    }
}
