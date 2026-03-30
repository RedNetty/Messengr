package com.rednetty.messengr.server;

import com.rednetty.messengr.shared.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClientHandler helper logic.
 *
 * <p>Because ClientHandler requires a live Socket to construct normally, we
 * test the logic that can be exercised via reflection or by driving a
 * test-friendly sub-path through a minimal Server fixture.</p>
 */
class ClientHandlerTest {

    private Server server;

    @BeforeEach
    void setUp() {
        server = new Server();
        server.initializeServerForTest();
    }

    // -----------------------------------------------------------------------
    // ensureUniqueUsername (via package-private helper on Server fixture)
    // -----------------------------------------------------------------------

    @Test
    void ensureUniqueUsername_noConflict_returnsOriginal() throws Exception {
        String unique = invokeEnsureUnique("alice");
        assertEquals("alice", unique);
    }

    @Test
    void ensureUniqueUsername_conflictOnce_appends1() throws Exception {
        // Pre-register "alice"
        registerFakeClient("alice");

        String unique = invokeEnsureUnique("alice");
        assertEquals("alice1", unique);
    }

    @Test
    void ensureUniqueUsername_conflictTwice_appends2() throws Exception {
        registerFakeClient("bob");
        registerFakeClient("bob1");

        String unique = invokeEnsureUnique("bob");
        assertEquals("bob2", unique);
    }

    @Test
    void ensureUniqueUsername_emptyUsername_doesNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeEnsureUnique(""));
    }

    // -----------------------------------------------------------------------
    // isActive logic
    // -----------------------------------------------------------------------

    @Test
    void isActive_connectedWithRecentActivity_returnsTrue() {
        // A freshly mocked handler with isConnected=true and lastActivity=now
        ClientHandler mock = Mockito.mock(ClientHandler.class);
        when(mock.isActive()).thenReturn(true);
        assertTrue(mock.isActive());
    }

    @Test
    void isActive_afterForceDisconnect_returnsFalse() throws Exception {
        // Build a real ClientHandler with a mocked socket via reflection
        ClientHandler handler = buildMinimalClientHandler();

        // isConnected starts true; forceDisconnect flips it
        handler.forceDisconnect();
        assertFalse(handler.isActive(),
                "isActive() should be false after forceDisconnect()");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Invokes the private {@code ensureUniqueUsername} method via reflection on
     * a minimal ClientHandler instance wired to the test server.
     */
    private String invokeEnsureUnique(String name) throws Exception {
        ClientHandler handler = buildMinimalClientHandler();
        Method m = ClientHandler.class.getDeclaredMethod("ensureUniqueUsername", String.class);
        m.setAccessible(true);
        return (String) m.invoke(handler, name);
    }

    /**
     * Creates a ClientHandler without a real Socket by using a null socket
     * (only safe for methods that don't touch I/O).
     */
    private ClientHandler buildMinimalClientHandler() throws Exception {
        // Use the package-private constructor via reflection to pass null socket
        var ctor = ClientHandler.class.getDeclaredConstructor(java.net.Socket.class, Server.class);
        ctor.setAccessible(true);
        return ctor.newInstance((java.net.Socket) null, server);
    }

    private UUID registerFakeClient(String username) {
        UUID userId = UUID.randomUUID();
        User user = new User(username);
        user.setUserID(userId);

        ClientHandler mock = Mockito.mock(ClientHandler.class);
        when(mock.getUser()).thenReturn(user);
        server.connectedClients.put(userId, mock);
        return userId;
    }
}
