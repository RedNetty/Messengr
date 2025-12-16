package com.rednetty.messengr.server;

import com.rednetty.messengr.shared.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Server {
    private static final int PORT = 7234;
    private static final int MAX_CLIENTS = 100;
    private static final int MAX_MESSAGE_HISTORY = 50;
    private static final String DEFAULT_ROOM = "general";

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    ConcurrentHashMap<UUID, ClientHandler> connectedClients;
    private ConcurrentHashMap<String, ChatRoom> chatRooms;
    private List<ChatMessage> globalMessageHistory;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private AtomicInteger totalConnections = new AtomicInteger(0);
    private Set<String> bannedIPs = ConcurrentHashMap.newKeySet();
    private Properties serverConfig;

    public static void main(String[] args) {
        new Server().start();
    }

    public void start() {
        loadConfiguration();
        initializeServer();

        try {
            serverSocket = new ServerSocket(PORT);
            log(" Chat Server started on port " + PORT);
            log("Maximum clients: " + MAX_CLIENTS);
            log("Waiting for clients to connect...");

            while (isRunning.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIP = clientSocket.getInetAddress().getHostAddress();

                    if (bannedIPs.contains(clientIP)) {
                        log("Rejected connection from banned IP: " + clientIP);
                        clientSocket.close();
                        continue;
                    }

                    if (connectedClients.size() >= MAX_CLIENTS) {
                        log("Rejected connection: Server full (" + MAX_CLIENTS + " clients)");
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println("ERROR:Server is full. Please try again later.");
                        clientSocket.close();
                        continue;
                    }

                    totalConnections.incrementAndGet();
                    log("New client connected from " + clientIP + " (Total connections: " + totalConnections.get() + ")");

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    threadPool.submit(clientHandler);

                } catch (IOException e) {
                    if (isRunning.get()) {
                        log("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void loadConfiguration() {
        serverConfig = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("server.properties")) {
            if (input != null) {
                serverConfig.load(input);
                log("Configuration loaded successfully");
            }
        } catch (IOException e) {
            log("Using default configuration");
        }
    }

    private void initializeServer() {
        connectedClients = new ConcurrentHashMap<>();
        chatRooms = new ConcurrentHashMap<>();
        globalMessageHistory = Collections.synchronizedList(new ArrayList<>());
        threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

        // Create default chat room
        chatRooms.put(DEFAULT_ROOM, new ChatRoom(DEFAULT_ROOM, "General discussion"));

        // Start server maintenance thread
        startMaintenanceThread();
    }

    private void startMaintenanceThread() {
        Thread maintenanceThread = new Thread(() -> {
            while (isRunning.get()) {
                try {
                    Thread.sleep(30000); // Run every 30 seconds
                    performMaintenance();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        maintenanceThread.setDaemon(true);
        maintenanceThread.start();
    }

    private void performMaintenance() {
        // Clean up inactive connections
        List<UUID> toRemove = new ArrayList<>();
        connectedClients.forEach((userId, clientHandler) -> {
            if (!clientHandler.isActive()) {
                toRemove.add(userId);
            }
        });

        toRemove.forEach(this::removeClient);

        // Trim message history if too large
        synchronized (globalMessageHistory) {
            while (globalMessageHistory.size() > MAX_MESSAGE_HISTORY) {
                globalMessageHistory.remove(0);
            }
        }

        // Log server stats
        if (connectedClients.size() > 0) {
            log("Server stats - Active clients: " + connectedClients.size() +
                    ", Total rooms: " + chatRooms.size() +
                    ", Messages in history: " + globalMessageHistory.size());
        }
    }

    public void addClient(UUID userId, ClientHandler clientHandler) {
        connectedClients.put(userId, clientHandler);

        // Add to default room
        ChatRoom defaultRoom = chatRooms.get(DEFAULT_ROOM);
        if (defaultRoom != null) {
            defaultRoom.addUser(userId, clientHandler.getUser().getUsername());
            clientHandler.setCurrentRoom(DEFAULT_ROOM);
        }

        // Send recent message history
        sendRecentHistory(clientHandler);

        broadcastToRoom(DEFAULT_ROOM, "SERVER:" + clientHandler.getUser().getUsername() + " joined the chat", userId);
        log("Client added: " + clientHandler.getUser().getUsername() + " (Total: " + connectedClients.size() + ")");
    }

    public void removeClient(UUID userId) {
        ClientHandler clientHandler = connectedClients.remove(userId);
        if (clientHandler != null) {
            String username = clientHandler.getUser().getUsername();
            String currentRoom = clientHandler.getCurrentRoom();

            // Remove from all rooms
            chatRooms.values().forEach(room -> room.removeUser(userId));

            if (currentRoom != null) {
                broadcastToRoom(currentRoom, "SERVER:" + username + " left the chat", userId);
            }

            log("Client removed: " + username + " (Total: " + connectedClients.size() + ")");
        }
    }

    public void sendPrivateMessage(UUID fromUserId, String targetUsername, String message) {
        ClientHandler sender = connectedClients.get(fromUserId);
        if (sender == null) return;

        ClientHandler target = findClientByUsername(targetUsername);
        if (target == null) {
            sender.sendMessage("ERROR:User '" + targetUsername + "' not found or offline");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = "PRIVATE:[" + timestamp + "] " + sender.getUser().getUsername() + " -> " + targetUsername + ": " + message;

        target.sendMessage(formattedMessage);
        sender.sendMessage("PRIVATE_SENT:[" + timestamp + "] To " + targetUsername + ": " + message);

        log("Private message: " + sender.getUser().getUsername() + " -> " + targetUsername);
    }

    public void broadcastToRoom(String roomName, String message, UUID excludeUserId) {
        ChatRoom room = chatRooms.get(roomName);
        if (room == null) return;

        room.getUsers().forEach((userId, username) -> {
            if (!userId.equals(excludeUserId)) {
                ClientHandler client = connectedClients.get(userId);
                if (client != null) {
                    client.sendMessage(message);
                }
            }
        });

        // Add to message history if it's a regular message
        if (message.startsWith("MSG:")) {
            synchronized (globalMessageHistory) {
                globalMessageHistory.add(new ChatMessage(message, LocalDateTime.now(), roomName));
            }
        }
    }

    public void broadcastToAll(String message) {
        connectedClients.forEach((userId, clientHandler) -> clientHandler.sendMessage(message));
    }

    public boolean createRoom(String roomName, String description, UUID creatorId) {
        if (chatRooms.containsKey(roomName)) {
            return false;
        }

        ChatRoom newRoom = new ChatRoom(roomName, description);
        chatRooms.put(roomName, newRoom);
        log("Room created: " + roomName + " by " + connectedClients.get(creatorId).getUser().getUsername());
        return true;
    }

    public boolean joinRoom(UUID userId, String roomName) {
        ClientHandler client = connectedClients.get(userId);
        ChatRoom room = chatRooms.get(roomName);

        if (client == null || room == null) {
            return false;
        }

        // Leave current room
        String currentRoom = client.getCurrentRoom();
        if (currentRoom != null) {
            ChatRoom oldRoom = chatRooms.get(currentRoom);
            if (oldRoom != null) {
                oldRoom.removeUser(userId);
                broadcastToRoom(currentRoom, "SERVER:" + client.getUser().getUsername() + " left the room", userId);
            }
        }

        // Join new room
        room.addUser(userId, client.getUser().getUsername());
        client.setCurrentRoom(roomName);
        broadcastToRoom(roomName, "SERVER:" + client.getUser().getUsername() + " joined the room", userId);

        return true;
    }

    public String getRoomsList() {
        StringBuilder roomList = new StringBuilder("Available rooms:\n");
        chatRooms.forEach((name, room) -> {
            roomList.append("- ").append(name)
                    .append(" (").append(room.getUserCount()).append(" users): ")
                    .append(room.getDescription()).append("\n");
        });
        return roomList.toString();
    }

    public String getUsersInRoom(String roomName) {
        ChatRoom room = chatRooms.get(roomName);
        if (room == null) {
            return "Room not found: " + roomName;
        }

        return "Users in " + roomName + ": " +
                String.join(", ", room.getUsers().values());
    }

    public String getServerStats() {
        return String.format("Server Statistics:\n" +
                        "- Connected clients: %d/%d\n" +
                        "- Total connections: %d\n" +
                        "- Active rooms: %d\n" +
                        "- Messages processed: %d\n" +
                        "- Server uptime: Running",
                connectedClients.size(), MAX_CLIENTS,
                totalConnections.get(),
                chatRooms.size(),
                globalMessageHistory.size());
    }

    public boolean kickUser(String targetUsername, UUID adminId) {
        ClientHandler admin = connectedClients.get(adminId);
        if (admin == null || !admin.isAdmin()) {
            return false;
        }

        ClientHandler target = findClientByUsername(targetUsername);
        if (target == null) {
            return false;
        }

        target.sendMessage("KICKED:You have been kicked by " + admin.getUser().getUsername());
        target.forceDisconnect();

        broadcastToAll("SERVER:" + targetUsername + " was kicked by " + admin.getUser().getUsername());
        log("User kicked: " + targetUsername + " by " + admin.getUser().getUsername());

        return true;
    }

    private ClientHandler findClientByUsername(String username) {
        return connectedClients.values().stream()
                .filter(client -> client.getUser().getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    private void sendRecentHistory(ClientHandler client) {
        synchronized (globalMessageHistory) {
            globalMessageHistory.stream()
                    .limit(10) // Send last 10 messages
                    .forEach(msg -> client.sendMessage("HISTORY:" + msg.getContent()));
        }
    }

    void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + message);
    }

    private void shutdown() {
        log("Shutting down server...");
        isRunning.set(false);

        // Notify all clients
        broadcastToAll("SERVER:Server is shutting down. Goodbye!");
        broadcastToAll("DISCONNECT");

        // Close all client connections
        connectedClients.forEach((userId, clientHandler) -> clientHandler.close());
        connectedClients.clear();

        // Shutdown thread pool
        threadPool.shutdown();

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Error closing server socket: " + e.getMessage());
        }

        log("Server shutdown complete.");
    }
}

