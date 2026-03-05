package org.example.workforce.service;

import org.example.workforce.model.OfficeLocation;
import org.example.workforce.repository.OfficeLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoAttendanceServiceTest {

    @Mock
    private OfficeLocationRepository officeLocationRepository;

    @InjectMocks
    private GeoAttendanceService geoAttendanceService;

    private OfficeLocation mainOffice;
    private OfficeLocation branchOffice;

    @BeforeEach
    void setUp() {

        mainOffice = OfficeLocation.builder()
                .locationId(1)
                .locationName("Main Office")
                .latitude(17.385044)
                .longitude(78.486671)
                .radiusMeters(200)
                .isActive(true)
                .build();

        branchOffice = OfficeLocation.builder()
                .locationId(2)
                .locationName("Branch Office")
                .latitude(12.971599)
                .longitude(77.594566)
                .radiusMeters(500)
                .isActive(true)
                .build();
    }

    @Test
    void verifyLocation_NoActiveLocations_AllowsFreely() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of());

        var result = geoAttendanceService.verifyLocation(17.385044, 78.486671);

        assertTrue(result.withinFence());
        assertNull(result.officeLocationId());
        assertTrue(result.message().contains("not configured"));
    }

    @Test
    void verifyLocation_WithinMainOfficeFence_Success() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(mainOffice, branchOffice));

        var result = geoAttendanceService.verifyLocation(17.385100, 78.486700);

        assertTrue(result.withinFence());
        assertEquals(1, result.officeLocationId());
        assertTrue(result.message().contains("Main Office"));
    }

    @Test
    void verifyLocation_WithinBranchOfficeFence_Success() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(mainOffice, branchOffice));

        var result = geoAttendanceService.verifyLocation(12.971600, 77.594570);

        assertTrue(result.withinFence());
        assertEquals(2, result.officeLocationId());
        assertTrue(result.message().contains("Branch Office"));
    }

    @Test
    void verifyLocation_OutsideAllFences_Rejected() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(mainOffice, branchOffice));

        var result = geoAttendanceService.verifyLocation(28.613939, 77.209021);

        assertFalse(result.withinFence());
        assertTrue(result.distanceMeters() > 200);
        assertTrue(result.message().contains("away from the nearest office"));
    }

    @Test
    void verifyLocation_ExactlyAtOffice_Success() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(mainOffice));

        var result = geoAttendanceService.verifyLocation(17.385044, 78.486671);

        assertTrue(result.withinFence());
        assertEquals(1, result.officeLocationId());
        assertEquals(0, Math.round(result.distanceMeters()));
    }

    @Test
    void verifyLocation_AtEdgeOfRadius_Accepted() {

        OfficeLocation testOffice = OfficeLocation.builder()
                .locationId(1)
                .locationName("Test Office")
                .latitude(0.0)
                .longitude(0.0)
                .radiusMeters(200)
                .isActive(true)
                .build();
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(testOffice));

        var result = geoAttendanceService.verifyLocation(0.0009, 0.0);

        assertTrue(result.withinFence());
    }

    @Test
    void verifyLocation_JustOutsideRadius_Rejected() {
        OfficeLocation testOffice = OfficeLocation.builder()
                .locationId(1)
                .locationName("Test Office")
                .latitude(0.0)
                .longitude(0.0)
                .radiusMeters(100)
                .isActive(true)
                .build();
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(testOffice));

        var result = geoAttendanceService.verifyLocation(0.02, 0.0);

        assertFalse(result.withinFence());
        assertTrue(result.message().contains("Maximum allowed: 100m"));
    }

    @Test
    void verifyLocation_SingleActiveLocation_PicksNearest() {
        when(officeLocationRepository.findByIsActiveTrue()).thenReturn(List.of(mainOffice));

        var result = geoAttendanceService.verifyLocation(17.385100, 78.486700);

        assertTrue(result.withinFence());
        assertEquals(1, result.officeLocationId());
    }

    @Test
    void haversineDistance_SamePoint_ReturnsZero() {
        double distance = geoAttendanceService.haversineDistance(
                17.385044, 78.486671, 17.385044, 78.486671);

        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void haversineDistance_KnownDistance_Accurate() {

        double distance = geoAttendanceService.haversineDistance(
                28.613939, 77.209021,
                19.075983, 72.877655
        );

        assertTrue(distance > 1_100_000);
        assertTrue(distance < 1_200_000);
    }

    @Test
    void haversineDistance_ShortDistance_Accurate() {

        double distance = geoAttendanceService.haversineDistance(
                0.0, 0.0,
                0.001, 0.0
        );

        assertTrue(distance > 100);
        assertTrue(distance < 120);
    }

    @Test
    void haversineDistance_AntipodePoints_HalfCircumference() {

        double distance = geoAttendanceService.haversineDistance(0, 0, 0, 180);

        assertTrue(distance > 20_000_000);
        assertTrue(distance < 20_050_000);
    }

    @Test
    void haversineDistance_NegativeCoordinates_Works() {
        double distance = geoAttendanceService.haversineDistance(
                -33.8688, 151.2093,
                -37.8136, 144.9631
        );

        assertTrue(distance > 700_000);
        assertTrue(distance < 730_000);
    }
}
