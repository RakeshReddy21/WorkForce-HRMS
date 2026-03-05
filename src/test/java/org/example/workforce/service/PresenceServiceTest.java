package org.example.workforce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService();
    }

    @Test
    void userConnected_AddsUserToOnlineSet() {
        presenceService.userConnected("user@test.com");

        assertTrue(presenceService.isOnline("user@test.com"));
    }

    @Test
    void userConnected_MultipleUsers() {
        presenceService.userConnected("user1@test.com");
        presenceService.userConnected("user2@test.com");
        presenceService.userConnected("user3@test.com");

        assertTrue(presenceService.isOnline("user1@test.com"));
        assertTrue(presenceService.isOnline("user2@test.com"));
        assertTrue(presenceService.isOnline("user3@test.com"));
    }

    @Test
    void userConnected_DuplicateConnection_NoError() {
        presenceService.userConnected("user@test.com");
        presenceService.userConnected("user@test.com");

        assertTrue(presenceService.isOnline("user@test.com"));
        assertEquals(1, presenceService.getOnlineUsers().size());
    }

    @Test
    void userDisconnected_RemovesUserFromOnlineSet() {
        presenceService.userConnected("user@test.com");
        presenceService.userDisconnected("user@test.com");

        assertFalse(presenceService.isOnline("user@test.com"));
    }

    @Test
    void userDisconnected_NonExistentUser_NoError() {
        assertDoesNotThrow(() -> presenceService.userDisconnected("never-connected@test.com"));
    }

    @Test
    void userDisconnected_DoesNotAffectOtherUsers() {
        presenceService.userConnected("user1@test.com");
        presenceService.userConnected("user2@test.com");

        presenceService.userDisconnected("user1@test.com");

        assertFalse(presenceService.isOnline("user1@test.com"));
        assertTrue(presenceService.isOnline("user2@test.com"));
    }

    @Test
    void isOnline_ConnectedUser_ReturnsTrue() {
        presenceService.userConnected("user@test.com");

        assertTrue(presenceService.isOnline("user@test.com"));
    }

    @Test
    void isOnline_DisconnectedUser_ReturnsFalse() {
        assertFalse(presenceService.isOnline("user@test.com"));
    }

    @Test
    void isOnline_AfterReconnection_ReturnsTrue() {
        presenceService.userConnected("user@test.com");
        presenceService.userDisconnected("user@test.com");
        presenceService.userConnected("user@test.com");

        assertTrue(presenceService.isOnline("user@test.com"));
    }

    @Test
    void getOnlineUsers_ReturnsAllOnlineUsers() {
        presenceService.userConnected("user1@test.com");
        presenceService.userConnected("user2@test.com");

        Set<String> online = presenceService.getOnlineUsers();

        assertEquals(2, online.size());
        assertTrue(online.contains("user1@test.com"));
        assertTrue(online.contains("user2@test.com"));
    }

    @Test
    void getOnlineUsers_Empty_WhenNoUsersConnected() {
        Set<String> online = presenceService.getOnlineUsers();

        assertNotNull(online);
        assertTrue(online.isEmpty());
    }

    @Test
    void getOnlineUsers_ReturnsUnmodifiableSet() {
        presenceService.userConnected("user@test.com");

        Set<String> online = presenceService.getOnlineUsers();

        assertThrows(UnsupportedOperationException.class,
                () -> online.add("hacker@test.com"));
    }

    @Test
    void getOnlineUsers_ReflectsDisconnections() {
        presenceService.userConnected("user1@test.com");
        presenceService.userConnected("user2@test.com");
        presenceService.userDisconnected("user1@test.com");

        Set<String> online = presenceService.getOnlineUsers();

        assertEquals(1, online.size());
        assertTrue(online.contains("user2@test.com"));
    }
}
