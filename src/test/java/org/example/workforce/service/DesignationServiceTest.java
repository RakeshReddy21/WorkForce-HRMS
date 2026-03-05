package org.example.workforce.service;

import org.example.workforce.dto.DesignationRequest;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Designation;
import org.example.workforce.repository.DesignationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignationServiceTest {

    @Mock
    private DesignationRepository designationRepository;

    @InjectMocks
    private DesignationService designationService;

    private Designation designation;
    private DesignationRequest designationRequest;

    @BeforeEach
    void setUp() {
        designation = Designation.builder()
                .designationId(1)
                .designationName("Software Engineer")
                .description("Develops software applications")
                .isActive(true)
                .build();

        designationRequest = new DesignationRequest("Software Engineer", "Develops software applications");
    }

    @Test
    void createDesignation_Success() {
        when(designationRepository.existsByDesignationName("Software Engineer")).thenReturn(false);
        when(designationRepository.save(any(Designation.class))).thenReturn(designation);

        Designation result = designationService.createDesignation(designationRequest);

        assertNotNull(result);
        assertEquals("Software Engineer", result.getDesignationName());
        verify(designationRepository).save(any(Designation.class));
    }

    @Test
    void createDesignation_DuplicateName_ThrowsException() {
        when(designationRepository.existsByDesignationName("Software Engineer")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> designationService.createDesignation(designationRequest));

        verify(designationRepository, never()).save(any(Designation.class));
    }

    @Test
    void updateDesignation_Success() {
        DesignationRequest updateRequest = new DesignationRequest("Senior Engineer", "Senior software engineer");
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(designationRepository.findByDesignationName("Senior Engineer")).thenReturn(Optional.empty());
        when(designationRepository.save(any(Designation.class))).thenReturn(designation);

        Designation result = designationService.updateDesignation(1, updateRequest);

        assertNotNull(result);
        verify(designationRepository).save(any(Designation.class));
    }

    @Test
    void updateDesignation_NotFound_ThrowsException() {
        when(designationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> designationService.updateDesignation(99, designationRequest));
    }

    @Test
    void updateDesignation_DuplicateName_DifferentId_ThrowsException() {
        Designation existingOther = Designation.builder()
                .designationId(2)
                .designationName("Software Engineer")
                .build();
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(designationRepository.findByDesignationName("Software Engineer")).thenReturn(Optional.of(existingOther));

        assertThrows(DuplicateResourceException.class,
                () -> designationService.updateDesignation(1, designationRequest));
    }

    @Test
    void updateDesignation_SameName_SameId_Success() {
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(designationRepository.findByDesignationName("Software Engineer")).thenReturn(Optional.of(designation));
        when(designationRepository.save(any(Designation.class))).thenReturn(designation);

        Designation result = designationService.updateDesignation(1, designationRequest);

        assertNotNull(result);
        verify(designationRepository).save(any(Designation.class));
    }

    @Test
    void deactivateDesignation_Success() {
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(designationRepository.save(any(Designation.class))).thenReturn(designation);

        Designation result = designationService.deactivateDesignation(1);

        assertNotNull(result);
        assertFalse(result.getIsActive());
        verify(designationRepository).save(any(Designation.class));
    }

    @Test
    void deactivateDesignation_NotFound_ThrowsException() {
        when(designationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> designationService.deactivateDesignation(99));
    }

    @Test
    void deactivateDesignation_AlreadyDeactivated_ThrowsException() {
        designation.setIsActive(false);
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));

        assertThrows(InvalidActionException.class,
                () -> designationService.deactivateDesignation(1));
    }

    @Test
    void activateDesignation_Success() {
        designation.setIsActive(false);
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(designationRepository.save(any(Designation.class))).thenReturn(designation);

        Designation result = designationService.activateDesignation(1);

        assertNotNull(result);
        assertTrue(result.getIsActive());
        verify(designationRepository).save(any(Designation.class));
    }

    @Test
    void activateDesignation_NotFound_ThrowsException() {
        when(designationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> designationService.activateDesignation(99));
    }

    @Test
    void activateDesignation_AlreadyActive_ThrowsException() {
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));

        assertThrows(InvalidActionException.class,
                () -> designationService.activateDesignation(1));
    }

    @Test
    void getAllDesignations_Success() {
        Designation desig2 = Designation.builder().designationId(2).designationName("Manager").build();
        when(designationRepository.findAll()).thenReturn(Arrays.asList(designation, desig2));

        List<Designation> result = designationService.getAllDesignations();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void getAllDesignations_EmptyList() {
        when(designationRepository.findAll()).thenReturn(List.of());

        List<Designation> result = designationService.getAllDesignations();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getDesignationById_Success() {
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));

        Designation result = designationService.getDesignationById(1);

        assertNotNull(result);
        assertEquals(1, result.getDesignationId());
        assertEquals("Software Engineer", result.getDesignationName());
    }

    @Test
    void getDesignationById_NotFound_ThrowsException() {
        when(designationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> designationService.getDesignationById(99));
    }
}
