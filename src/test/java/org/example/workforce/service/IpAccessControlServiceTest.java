package org.example.workforce.service;

import org.example.workforce.dto.IpRangeRequest;
import org.example.workforce.exception.BadRequestException;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.AllowedIpRange;
import org.example.workforce.repository.AllowedIpRangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.workforce.util.NetworkIpUtil;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpAccessControlServiceTest {

    @Mock
    private AllowedIpRangeRepository ipRangeRepository;

    @InjectMocks
    private IpAccessControlService ipAccessControlService;

    private AllowedIpRange ipRange;
    private IpRangeRequest request;

    @BeforeEach
    void setUp() {
        ipRange = AllowedIpRange.builder()
                .ipRangeId(1)
                .ipRange("192.168.1.0/24")
                .description("Office Network")
                .isActive(true)
                .build();

        request = new IpRangeRequest();
        request.setIpRange("192.168.1.0/24");
        request.setDescription("Office Network");
        request.setIsActive(true);
    }

    @Test
    void getAllIpRanges_Success() {
        when(ipRangeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(ipRange));

        List<AllowedIpRange> result = ipAccessControlService.getAllIpRanges();

        assertEquals(1, result.size());
        assertEquals("Office Network", result.get(0).getDescription());
    }

    @Test
    void getAllIpRanges_Empty() {
        when(ipRangeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<AllowedIpRange> result = ipAccessControlService.getAllIpRanges();

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveIpRanges_Success() {
        when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(ipRange));

        List<AllowedIpRange> result = ipAccessControlService.getActiveIpRanges();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
    }

    @Test
    void addIpRange_Success() {
        when(ipRangeRepository.existsByIpRange("192.168.1.0/24")).thenReturn(false);
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenReturn(ipRange);

        AllowedIpRange result = ipAccessControlService.addIpRange(request);

        assertNotNull(result);
        assertEquals("192.168.1.0/24", result.getIpRange());
        verify(ipRangeRepository).save(any(AllowedIpRange.class));
    }

    @Test
    void addIpRange_DuplicateIp_ThrowsException() {
        when(ipRangeRepository.existsByIpRange("192.168.1.0/24")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> ipAccessControlService.addIpRange(request));
    }

    @Test
    void addIpRange_InvalidIp_ThrowsException() {
        IpRangeRequest badRequest = new IpRangeRequest();
        badRequest.setIpRange("not.an.ip.address");
        badRequest.setDescription("Test");

        assertThrows(BadRequestException.class,
                () -> ipAccessControlService.addIpRange(badRequest));
    }

    @Test
    void addIpRange_SingleIp_Success() {
        IpRangeRequest singleIpReq = new IpRangeRequest();
        singleIpReq.setIpRange("10.0.0.1");
        singleIpReq.setDescription("Single IP");

        AllowedIpRange savedRange = AllowedIpRange.builder()
                .ipRangeId(2).ipRange("10.0.0.1").description("Single IP").isActive(true).build();

        when(ipRangeRepository.existsByIpRange("10.0.0.1")).thenReturn(false);
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenReturn(savedRange);

        AllowedIpRange result = ipAccessControlService.addIpRange(singleIpReq);

        assertNotNull(result);
        assertEquals("10.0.0.1", result.getIpRange());
    }

    @Test
    void addIpRange_InvalidCidrPrefix_ThrowsException() {
        IpRangeRequest badCidr = new IpRangeRequest();
        badCidr.setIpRange("192.168.1.0/33");
        badCidr.setDescription("Bad CIDR");

        assertThrows(BadRequestException.class,
                () -> ipAccessControlService.addIpRange(badCidr));
    }

    @Test
    void addIpRange_NullIsActive_DefaultsToTrue() {
        request.setIsActive(null);
        when(ipRangeRepository.existsByIpRange("192.168.1.0/24")).thenReturn(false);
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenAnswer(inv -> {
            AllowedIpRange saved = inv.getArgument(0);
            assertTrue(saved.getIsActive());
            return saved;
        });

        ipAccessControlService.addIpRange(request);

        verify(ipRangeRepository).save(any(AllowedIpRange.class));
    }

    @Test
    void updateIpRange_Success() {
        IpRangeRequest updateReq = new IpRangeRequest();
        updateReq.setIpRange("10.0.0.0/8");
        updateReq.setDescription("Updated Network");
        updateReq.setIsActive(false);

        when(ipRangeRepository.findById(1)).thenReturn(Optional.of(ipRange));
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenReturn(ipRange);

        AllowedIpRange result = ipAccessControlService.updateIpRange(1, updateReq);

        assertNotNull(result);
        verify(ipRangeRepository).save(any(AllowedIpRange.class));
    }

    @Test
    void updateIpRange_NotFound_ThrowsException() {
        IpRangeRequest updateReq = new IpRangeRequest();
        updateReq.setDescription("Updated");

        when(ipRangeRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ipAccessControlService.updateIpRange(99, updateReq));
    }

    @Test
    void updateIpRange_NullFields_NoChange() {
        IpRangeRequest updateReq = new IpRangeRequest();

        when(ipRangeRepository.findById(1)).thenReturn(Optional.of(ipRange));
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenReturn(ipRange);

        AllowedIpRange result = ipAccessControlService.updateIpRange(1, updateReq);

        assertEquals("192.168.1.0/24", result.getIpRange());
    }

    @Test
    void toggleIpRange_ActiveToInactive() {
        ipRange.setIsActive(true);
        when(ipRangeRepository.findById(1)).thenReturn(Optional.of(ipRange));
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenAnswer(i -> i.getArgument(0));

        AllowedIpRange result = ipAccessControlService.toggleIpRange(1);

        assertFalse(result.getIsActive());
    }

    @Test
    void toggleIpRange_InactiveToActive() {
        ipRange.setIsActive(false);
        when(ipRangeRepository.findById(1)).thenReturn(Optional.of(ipRange));
        when(ipRangeRepository.save(any(AllowedIpRange.class))).thenAnswer(i -> i.getArgument(0));

        AllowedIpRange result = ipAccessControlService.toggleIpRange(1);

        assertTrue(result.getIsActive());
    }

    @Test
    void toggleIpRange_NotFound_ThrowsException() {
        when(ipRangeRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ipAccessControlService.toggleIpRange(99));
    }

    @Test
    void deleteIpRange_Success() {
        when(ipRangeRepository.existsById(1)).thenReturn(true);
        doNothing().when(ipRangeRepository).deleteById(1);

        ipAccessControlService.deleteIpRange(1);

        verify(ipRangeRepository).deleteById(1);
    }

    @Test
    void deleteIpRange_NotFound_ThrowsException() {
        when(ipRangeRepository.existsById(99)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> ipAccessControlService.deleteIpRange(99));
    }

    @Test
    void isIpAllowed_NoActiveRanges_AllowsAll() {
        when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of());

        assertTrue(ipAccessControlService.isIpAllowed("1.2.3.4"));
    }

    @Test
    void isIpAllowed_ExactIpMatch_Allowed() {
        AllowedIpRange exactIp = AllowedIpRange.builder()
                .ipRangeId(2).ipRange("10.0.0.5").description("Dev").isActive(true).build();

        try (MockedStatic<NetworkIpUtil> util = mockStatic(NetworkIpUtil.class)) {
            util.when(() -> NetworkIpUtil.isLoopback("10.0.0.5")).thenReturn(false);
            when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(exactIp));

            assertTrue(ipAccessControlService.isIpAllowed("10.0.0.5"));
        }
    }

    @Test
    void isIpAllowed_CidrMatch_Allowed() {
        try (MockedStatic<NetworkIpUtil> util = mockStatic(NetworkIpUtil.class)) {
            util.when(() -> NetworkIpUtil.isLoopback("192.168.1.100")).thenReturn(false);
            when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(ipRange));

            assertTrue(ipAccessControlService.isIpAllowed("192.168.1.100"));
        }
    }

    @Test
    void isIpAllowed_OutsideCidr_Denied() {
        try (MockedStatic<NetworkIpUtil> util = mockStatic(NetworkIpUtil.class)) {
            util.when(() -> NetworkIpUtil.isLoopback("10.10.10.10")).thenReturn(false);
            when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(ipRange));

            assertFalse(ipAccessControlService.isIpAllowed("10.10.10.10"));
        }
    }

    @Test
    void isIpAllowed_LoopbackIp_NormalizedToNetworkIp() {
        AllowedIpRange localNet = AllowedIpRange.builder()
                .ipRangeId(3).ipRange("192.168.1.0/24").description("Local").isActive(true).build();

        try (MockedStatic<NetworkIpUtil> util = mockStatic(NetworkIpUtil.class)) {
            util.when(() -> NetworkIpUtil.isLoopback("127.0.0.1")).thenReturn(true);
            util.when(NetworkIpUtil::getLocalNetworkIp).thenReturn("192.168.1.50");
            when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(localNet));

            assertTrue(ipAccessControlService.isIpAllowed("127.0.0.1"));
        }
    }

    @Test
    void isIpAllowed_NullIp_Handled() {
        when(ipRangeRepository.findByIsActiveTrue()).thenReturn(List.of(ipRange));

        assertDoesNotThrow(() -> ipAccessControlService.isIpAllowed(null));
    }
}
