package com.rednetty.messengr.server;

import com.rednetty.messengr.shared.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Server business logic.
 *
 * <p>Tests that do not require a real socket/ServerSocket are exercised directly.
 * For methods that depend on connected clients we inject mocked ClientHandlers
 * via package-private field access.</p>
 */
class ServerTest {

    private Server server;

    @BeforeEach
    void setUp() {
        server = new Server();
        // Initialise internal state without binding a socket
        server.initializeServerForTest();
    }

    // -----------------------------------------------------------------------
    // createRoom
    // -----------------------------------------------------------------------

    @Test
    void createRoom_newRoom_returnsTrue() {
        UUID creator = registerFakeClient("alice", false);
        boolean result = server.createRoom("newroom", "desc", creator);
        assertTrue(result, "Creating a brand-new room should return true");
    }

    @Test
    void createRoom_duplicateRoom_returnsFalse() {
        UUID creator = registerFakeClient("bob", false);
        server.createRoom("duperoom", "first", creator);
        boolean result = server.createRoom("duperoom", "second", creator);
        assertFalse(result, "Creating a room that already exists should return false");
    }

    // -----------------------------------------------------------------------
    // kickUser
    // -----------------------------------------------------------------------

    @Test
    void kickUser_byNonAdmin_returnsFalse() {
        UUID adminId = registerFakeClient("admin", false); // not actually admin
        UUID targetId = registerFakeClient("victim", false);
        boolean kicked = server.kickUser("victim", adminId);
        assertFalse(kicked, "A non-admin should not be able to kick users");
    }

    @Test
    void kickUser_byAdmin_returnsTrue() {
        UUID adminId = registerFakeClient("superadmin", true);
        UUID targetId = registerFakeClient("victim", false);

        boolean kicked = server.kickUser("victim", adminId);
        assertTrue(kicked, "An admin should be able to kick a connected user");
    }

    @Test
    void kickUser_unknownTarget_returnsFalse() {
        UUID adminId = registerFakeClient("superadmin", true);
        boolean kicked = server.kickUser("nobody", adminId);
        assertFalse(kicked, "Kicking a user that does not exist should return false");
    }

    // -----------------------------------------------------------------------
    // getRoomsList
    // -----------------------------------------------------------------------

    @Test
    void getRoomsList_containsDefaultRoom() {
        String list = server.getRoomsList();
        assertTrue(list.contains("general"),
                "Rooms list should always contain the default 'general' room");
    }

    @Test
    void getRoomsList_formatIncludesDashPrefix() {
        String list = server.getRoomsList();
        // Every room line starts with "- "
        assertTrue(list.lines().anyMatch(l -> l.trim().startsWith("- ")),
                "Each room entry should start with '- '");
    }

    @Test
    void getRoomsList_includesUserCount() {
        String list = server.getRoomsList();
        // Should contain parenthesised user count like "(0 users)"
        assertTrue(list.contains("users)"),
                "Rooms list should show user count in parentheses");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Registers a mocked ClientHandler in the server's connectedClients map.
     *
     * @param username the username to register
     * @param admin    whether the mock handler reports isAdmin() == true
     * @return the UUID assigned to the fake client
     */
    private UUID registerFakeClient(String username, boolean admin) {
        UUID userId = UUID.randomUUID();
        User user = new User(username);
        // Force the UUID to match so lookups work
        user.setUserID(userId);

        ClientHandler mock = Mockito.mock(ClientHandler.class);
        when(mock.getUser()).thenReturn(user);
        when(mock.isAdmin()).thenReturn(admin);
        when(mock.isActive()).thenReturn(true);
        when(mock.getCurrentRoom()).thenReturn("general");
        // forceDisconnect / sendMessage are void — do nothing by default

        server.connectedClients.put(userId, mock);
        return userId;
    }
}
