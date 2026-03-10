package org.example.workforce.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void testPasswordEncoderBean() {
        assertNotNull(passwordEncoder);
        String encoded = passwordEncoder.encode("testPassword");
        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches("testPassword", encoded));
    }

    @Test
    void testAuthenticationManagerBean() {
        assertNotNull(authenticationManager);
    }
}

