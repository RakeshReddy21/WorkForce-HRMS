package org.example.workforce.service;

import org.example.workforce.dto.OfficeLocationRequest;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.OfficeLocation;
import org.example.workforce.repository.OfficeLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfficeLocationServiceTest {

    @Mock
    private OfficeLocationRepository officeLocationRepository;

    @InjectMocks
    private OfficeLocationService officeLocationService;

    private OfficeLocation location;
    private OfficeLocationRequest request;

    @BeforeEach
    void setUp() {
        location = OfficeLocation.builder()
                .locationId(1)
                .locationName("Main Office")
                .address("123 Main St")
                .latitude(17.385044)
                .longitude(78.486671)
                .radiusMeters(200)
                .isActive(true)
                .build();

        request = new OfficeLocationRequest();
        request.setLocationName("Main Office");
        request.setAddress("123 Main St");
        request.setLatitude(17.385044);
        request.setLongitude(78.486671);
        request.setRadiusMeters(200);
        request.setIsActive(true);
    }

    @Test
    void getAllLocations_Success() {
        when(officeLocationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(location));

        List<OfficeLocation> result = officeLocationService.getAllLocations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Main Office", result.get(0).getLocationName());
    }

    @Test
    void getAllLocations_Empty() {
        when(officeLocationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<OfficeLocation> result = officeLocationService.getAllLocations();

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveLocations_Success() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(location));

        List<OfficeLocation> result = officeLocationService.getActiveLocations();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
    }

    @Test
    void getLocationById_Success() {
        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));

        OfficeLocation result = officeLocationService.getLocationById(1);

        assertNotNull(result);
        assertEquals("Main Office", result.getLocationName());
    }

    @Test
    void getLocationById_NotFound_ThrowsException() {
        when(officeLocationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> officeLocationService.getLocationById(99));
    }

    @Test
    void addLocation_Success() {
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenReturn(location);

        OfficeLocation result = officeLocationService.addLocation(request);

        assertNotNull(result);
        assertEquals("Main Office", result.getLocationName());
        assertEquals(200, result.getRadiusMeters());
        verify(officeLocationRepository).save(any(OfficeLocation.class));
    }

    @Test
    void addLocation_DuplicateName_ThrowsException() {
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> officeLocationService.addLocation(request));
    }

    @Test
    void addLocation_InvalidLatitude_ThrowsException() {
        request.setLatitude(91.0);
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> officeLocationService.addLocation(request));
    }

    @Test
    void addLocation_InvalidLongitude_ThrowsException() {
        request.setLongitude(181.0);
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> officeLocationService.addLocation(request));
    }

    @Test
    void addLocation_NegativeInvalidLatitude_ThrowsException() {
        request.setLatitude(-91.0);
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> officeLocationService.addLocation(request));
    }

    @Test
    void addLocation_NullRadius_DefaultsTo200() {
        request.setRadiusMeters(null);
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenAnswer(inv -> {
            OfficeLocation saved = inv.getArgument(0);
            assertEquals(200, saved.getRadiusMeters());
            return saved;
        });

        officeLocationService.addLocation(request);

        verify(officeLocationRepository).save(any(OfficeLocation.class));
    }

    @Test
    void addLocation_NullIsActive_DefaultsToTrue() {
        request.setIsActive(null);
        when(officeLocationRepository.existsByLocationName("Main Office")).thenReturn(false);
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenAnswer(inv -> {
            OfficeLocation saved = inv.getArgument(0);
            assertTrue(saved.getIsActive());
            return saved;
        });

        officeLocationService.addLocation(request);

        verify(officeLocationRepository).save(any(OfficeLocation.class));
    }

    @Test
    void updateLocation_Success() {
        OfficeLocationRequest updateReq = new OfficeLocationRequest();
        updateReq.setLocationName("Updated Office");
        updateReq.setAddress("456 New St");
        updateReq.setLatitude(18.0);
        updateReq.setLongitude(79.0);
        updateReq.setRadiusMeters(300);

        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));
        when(officeLocationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(location));
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenReturn(location);

        OfficeLocation result = officeLocationService.updateLocation(1, updateReq);

        assertNotNull(result);
        verify(officeLocationRepository).save(any(OfficeLocation.class));
    }

    @Test
    void updateLocation_DuplicateName_ThrowsException() {
        OfficeLocation otherLoc = OfficeLocation.builder()
                .locationId(2).locationName("Branch Office").build();

        OfficeLocationRequest updateReq = new OfficeLocationRequest();
        updateReq.setLocationName("Branch Office");
        updateReq.setLatitude(18.0);
        updateReq.setLongitude(79.0);

        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));
        when(officeLocationRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(location, otherLoc));

        assertThrows(DuplicateResourceException.class,
                () -> officeLocationService.updateLocation(1, updateReq));
    }

    @Test
    void updateLocation_NotFound_ThrowsException() {
        OfficeLocationRequest updateReq = new OfficeLocationRequest();
        updateReq.setLocationName("Test");
        updateReq.setLatitude(18.0);
        updateReq.setLongitude(79.0);

        when(officeLocationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> officeLocationService.updateLocation(99, updateReq));
    }

    @Test
    void toggleLocation_ActiveToInactive() {
        location.setIsActive(true);
        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenAnswer(i -> i.getArgument(0));

        OfficeLocation result = officeLocationService.toggleLocation(1);

        assertFalse(result.getIsActive());
    }

    @Test
    void toggleLocation_InactiveToActive() {
        location.setIsActive(false);
        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));
        when(officeLocationRepository.save(any(OfficeLocation.class))).thenAnswer(i -> i.getArgument(0));

        OfficeLocation result = officeLocationService.toggleLocation(1);

        assertTrue(result.getIsActive());
    }

    @Test
    void toggleLocation_NotFound_ThrowsException() {
        when(officeLocationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> officeLocationService.toggleLocation(99));
    }

    @Test
    void deleteLocation_Success() {
        when(officeLocationRepository.findById(1)).thenReturn(Optional.of(location));
        doNothing().when(officeLocationRepository).delete(location);

        officeLocationService.deleteLocation(1);

        verify(officeLocationRepository).delete(location);
    }

    @Test
    void deleteLocation_NotFound_ThrowsException() {
        when(officeLocationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> officeLocationService.deleteLocation(99));
    }
}
