package org.example.workforce.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        SecretKey key = Jwts.SIG.HS256.key().build();
        String base64Secret = Encoders.BASE64.encode(key.getEncoded());
        ReflectionTestUtils.setField(jwtUtil, "secretKey", base64Secret);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 86400000L);

        userDetails = User.withUsername("test@example.com")
                .password("password")
                .roles("EMPLOYEE")
                .build();
    }

    @Test
    void testGenerateToken() {
        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateTokenWithExtraClaims() {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "EMPLOYEE");
        String token = jwtUtil.generateToken(extraClaims, userDetails);
        assertNotNull(token);
    }

    @Test
    void testExtractEmail() {
        String token = jwtUtil.generateToken(userDetails);
        String email = jwtUtil.extractEmail(token);
        assertEquals("test@example.com", email);
    }

    @Test
    void testExtractRole() {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "EMPLOYEE");
        String token = jwtUtil.generateToken(extraClaims, userDetails);
        String role = jwtUtil.extractRole(token);
        assertEquals("EMPLOYEE", role);
    }

    @Test
    void testIsTokenValid() {
        String token = jwtUtil.generateToken(userDetails);
        assertTrue(jwtUtil.isTokenValid(token, userDetails));
    }

    @Test
    void testExtractExpiration() {
        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(jwtUtil.extractExpiration(token));
    }
}
