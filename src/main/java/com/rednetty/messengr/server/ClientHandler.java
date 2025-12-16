package com.rednetty.messengr.server;

import com.rednetty.messengr.shared.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private User user;
    private Server server;
    private AtomicBoolean isConnected = new AtomicBoolean(true);
    private String currentRoom = "general";
    private boolean isAdmin = false;
    private LocalDateTime lastActivity = LocalDateTime.now();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClientHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            setupStreams();
            if (authenticateUser()) {
                server.addClient(user.getUserID(), this);
                sendMessage("SERVER:Welcome to the  Chat Server!");
                sendMessage("SERVER:Type /help for available commands");
                handleClientMessages();
            }
        } catch (IOException e) {
            server.log("Error handling client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    private boolean authenticateUser() throws IOException {
        String authRequest = in.readLine();

        if (authRequest == null || !authRequest.startsWith("AUTH:")) {
            sendMessage("ERROR:Invalid authentication format");
            return false;
        }

        String username = authRequest.substring(5).trim();
        if (username.isEmpty()) {
            username = "Guest";
        }

        // Check for admin password
        if (username.contains(":admin:password123")) {
            username = username.replace(":admin:password123", "");
            isAdmin = true;
        }

        // Ensure unique username
        String finalUsername = ensureUniqueUsername(username);

        user = new User(finalUsername);
        sendMessage("AUTH_SUCCESS:" + user.getUsername() + "," + user.getUserID());

        if (isAdmin) {
            sendMessage("SERVER:Admin privileges granted");
        }

        return true;
    }

    private String ensureUniqueUsername(String requestedUsername) {
        String username = requestedUsername;
        int counter = 1;

        while (isUsernameTaken(username)) {
            username = requestedUsername + counter;
            counter++;
        }

        return username;
    }

    private boolean isUsernameTaken(String username) {
        return server.connectedClients.values().stream()
                .anyMatch(client -> client.getUser() != null &&
                        client.getUser().getUsername().equals(username));
    }

    private void handleClientMessages() throws IOException {
        String inputLine;

        while (isConnected.get() && (inputLine = in.readLine()) != null) {
            lastActivity = LocalDateTime.now();

            if (inputLine.equals("DISCONNECT")) {
                break;
            }

            processMessage(inputLine);
        }
    }

    private void processMessage(String message) {
        if (message.startsWith("MSG:")) {
            handleChatMessage(message);
        } else if (message.startsWith("CMD:")) {
            handleCommand(message);
        }
    }

    private void handleChatMessage(String message) {
        String[] parts = message.split(":", 3);
        if (parts.length == 3) {
            String messageContent = parts[2];
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String formattedMessage = "MSG:[" + timestamp + "] " + user.getUsername() + ": " + messageContent;
            server.broadcastToRoom(currentRoom, formattedMessage, user.getUserID());
        }
    }

    private void handleCommand(String command) {
        String cmd = command.substring(4).trim();
        String[] parts = cmd.split(" ", 2);
        String action = parts[0].toLowerCase();

        switch (action) {
            case "help":
                sendHelp();
                break;
            case "users":
                sendMessage("SERVER:" + server.getUsersInRoom(currentRoom));
                break;
            case "rooms":
                sendMessage("SERVER:" + server.getRoomsList());
                break;
            case "join":
                if (parts.length > 1) {
                    if (server.joinRoom(user.getUserID(), parts[1])) {
                        sendMessage("SERVER:Joined room: " + parts[1]);
                    } else {
                        sendMessage("ERROR:Could not join room: " + parts[1]);
                    }
                }
                break;
            case "create":
                if (parts.length > 1) {
                    String[] roomParts = parts[1].split(" ", 2);
                    String roomName = roomParts[0];
                    String description = roomParts.length > 1 ? roomParts[1] : "No description";

                    if (server.createRoom(roomName, description, user.getUserID())) {
                        sendMessage("SERVER:Room created: " + roomName);
                    } else {
                        sendMessage("ERROR:Room already exists: " + roomName);
                    }
                }
                break;
            case "msg":
            case "pm":
                if (parts.length > 1) {
                    String[] msgParts = parts[1].split(" ", 2);
                    if (msgParts.length == 2) {
                        server.sendPrivateMessage(user.getUserID(), msgParts[0], msgParts[1]);
                    }
                }
                break;
            case "stats":
                sendMessage("SERVER:" + server.getServerStats());
                break;
            case "kick":
                if (isAdmin && parts.length > 1) {
                    if (server.kickUser(parts[1], user.getUserID())) {
                        sendMessage("SERVER:User kicked: " + parts[1]);
                    } else {
                        sendMessage("ERROR:Could not kick user: " + parts[1]);
                    }
                } else {
                    sendMessage("ERROR:Insufficient privileges");
                }
                break;
            default:
                sendMessage("ERROR:Unknown command. Type /help for available commands.");
        }
    }

    private void sendHelp() {
        StringBuilder help = new StringBuilder("Available commands:\n");
        help.append("/help - Show this help message\n");
        help.append("/users - List users in current room\n");
        help.append("/rooms - List all available rooms\n");
        help.append("/join <room> - Join a chat room\n");
        help.append("/create <room> [description] - Create a new room\n");
        help.append("/msg <user> <message> - Send private message\n");
        help.append("/stats - Show server statistics\n");

        if (isAdmin) {
            help.append("/kick <user> - Kick a user (admin only)\n");
        }

        help.append("/quit - Exit the chat");
        sendMessage("SERVER:" + help.toString());
    }

    public void sendMessage(String message) {
        if (out != null && isConnected.get()) {
            out.println(message);
        }
    }

    public void forceDisconnect() {
        isConnected.set(false);
        close();
    }

    public boolean isActive() {
        return isConnected.get() &&
                lastActivity.isAfter(LocalDateTime.now().minusMinutes(30));
    }

    public User getUser() {
        return user;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String room) {
        this.currentRoom = room;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void close() {
        isConnected.set(false);

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            // Ignore exceptions during cleanup
        }
    }

    private void cleanup() {
        if (user != null) {
            server.removeClient(user.getUserID());
        }
        close();
    }
}
