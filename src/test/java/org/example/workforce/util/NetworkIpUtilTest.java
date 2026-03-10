package org.example.workforce.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkIpUtilTest {

    @Test
    void testIsLoopback_Ipv4() {
        assertTrue(NetworkIpUtil.isLoopback("127.0.0.1"));
    }

    @Test
    void testIsLoopback_Ipv6Short() {
        assertTrue(NetworkIpUtil.isLoopback("::1"));
    }

    @Test
    void testIsLoopback_Ipv6Long() {
        assertTrue(NetworkIpUtil.isLoopback("0:0:0:0:0:0:0:1"));
    }

    @Test
    void testIsLoopback_Null() {
        assertFalse(NetworkIpUtil.isLoopback(null));
    }

    @Test
    void testIsLoopback_NotLoopback() {
        assertFalse(NetworkIpUtil.isLoopback("192.168.1.1"));
    }

    @Test
    void testResolveClientIp_FromXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String result = NetworkIpUtil.resolveClientIp(request);
        assertEquals("192.168.1.100", result);
    }

    @Test
    void testResolveClientIp_FromXRealIP() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.50");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String result = NetworkIpUtil.resolveClientIp(request);
        assertEquals("10.0.0.50", result);
    }

    @Test
    void testResolveClientIp_FromRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.200");

        String result = NetworkIpUtil.resolveClientIp(request);
        assertEquals("192.168.1.200", result);
    }

    @Test
    void testResolveClientIp_LoopbackInXFF() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");

        // Loopback in XFF is skipped, falls through to remoteAddr
        String result = NetworkIpUtil.resolveClientIp(request);
        assertEquals("192.168.1.50", result);
    }

    @Test
    void testResolveLoopbackToLanIp_Null() {
        assertEquals("", NetworkIpUtil.resolveLoopbackToLanIp(null));
    }

    @Test
    void testResolveLoopbackToLanIp_NonLoopback() {
        assertEquals("192.168.1.1", NetworkIpUtil.resolveLoopbackToLanIp("192.168.1.1"));
    }

    @Test
    void testGetLocalNetworkIp() {
        // This may return null depending on the environment
        // Just verify it doesn't throw
        assertDoesNotThrow(() -> NetworkIpUtil.getLocalNetworkIp());
    }
}





