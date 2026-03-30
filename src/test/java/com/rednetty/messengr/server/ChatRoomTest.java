package com.rednetty.messengr.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatRoom.
 */
class ChatRoomTest {

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = new ChatRoom("test-room", "A room for testing");
    }

    @Test
    void addUser_shouldIncreaseUserCount() {
        UUID id = UUID.randomUUID();
        room.addUser(id, "alice");
        assertEquals(1, room.getUserCount());
        assertTrue(room.getUsers().containsKey(id));
        assertEquals("alice", room.getUsers().get(id));
    }

    @Test
    void addUser_multipleUsers_shouldTrackAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        room.addUser(id1, "alice");
        room.addUser(id2, "bob");
        assertEquals(2, room.getUserCount());
    }

    @Test
    void removeUser_shouldDecreaseUserCount() {
        UUID id = UUID.randomUUID();
        room.addUser(id, "alice");
        room.removeUser(id);
        assertEquals(0, room.getUserCount());
        assertFalse(room.getUsers().containsKey(id));
    }

    @Test
    void removeUser_nonexistentId_shouldBeNoop() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> room.removeUser(id));
        assertEquals(0, room.getUserCount());
    }

    @Test
    void getUserCount_emptyRoom_returnsZero() {
        assertEquals(0, room.getUserCount());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("test-room", room.getName());
    }

    @Test
    void getDescription_returnsCorrectDescription() {
        assertEquals("A room for testing", room.getDescription());
    }

    @Test
    void createdAt_isNotNull() {
        assertNotNull(room.getCreatedAt());
    }

    @Test
    void addUser_duplicateId_overwritesUsername() {
        UUID id = UUID.randomUUID();
        room.addUser(id, "alice");
        room.addUser(id, "alice-renamed");
        // ConcurrentHashMap.put replaces — count stays 1
        assertEquals(1, room.getUserCount());
        assertEquals("alice-renamed", room.getUsers().get(id));
    }
}
