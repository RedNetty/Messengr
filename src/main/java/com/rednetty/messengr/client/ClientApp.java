package com.rednetty.messengr.client;

import com.rednetty.messengr.shared.User;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientApp {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 7234;
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner;
    private User currentUser;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private Thread messageListener;
    private String currentRoom = "general";
    private boolean colorSupport = true;
    private int reconnectAttempts = 0;
    private final int maxReconnectAttempts = 5;

    public static void main(String[] args) {
        new ClientApp().start();
    }

    public void start() {
        displayWelcomeMessage();

        while (isRunning.get() && reconnectAttempts <= maxReconnectAttempts) {
            try {
                if (connectToServer()) {
                    if (authenticateUser()) {
                        startMessageListener();
                        handleUserInput();
                    }
                }
            } catch (Exception e) {
                printError("Connection error: " + e.getMessage());

                if (isRunning.get()) {
                    printInfo("Attempting to reconnect...");
                    reconnectAttempts++;

                    if (reconnectAttempts <= maxReconnectAttempts) {
                        try {
                            Thread.sleep(3000 * reconnectAttempts); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        if (reconnectAttempts > maxReconnectAttempts) {
            printError("Maximum reconnection attempts reached. Exiting...");
        }

        shutdown();
    }

        private void displayWelcomeMessage() {
        System.out.println(colorize(ANSI_CYAN, "============================================"));
        System.out.println(colorize(ANSI_CYAN, "         Messengr Chat Client               "));
        System.out.println(colorize(ANSI_CYAN, "============================================"));
        System.out.println(colorize(ANSI_CYAN, "  [*] Multiple chat rooms                   "));
        System.out.println(colorize(ANSI_CYAN, "  [*] Private messaging                     "));
        System.out.println(colorize(ANSI_CYAN, "  [*] Advanced commands                     "));
        System.out.println(colorize(ANSI_CYAN, "  [*] Auto-reconnection                     "));
        System.out.println(colorize(ANSI_CYAN, "============================================"));
        System.out.println();
    }

    private boolean connectToServer() throws IOException {
        printInfo("Connecting to server at " + SERVER_HOST + ":" + SERVER_PORT + "...");

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            scanner = new Scanner(System.in);

            isConnected.set(true);
            reconnectAttempts = 0; // Reset on successful connection
            printSuccess("Connected to server!");
            return true;

        } catch (ConnectException e) {
            throw new IOException("Server is not available. Please make sure the server is running.");
        }
    }

    private boolean authenticateUser() throws IOException {
        if (currentUser == null) {
            printInfo("Authentication required");
            System.out.print(colorize(ANSI_YELLOW, "Enter your username: "));
            String username = scanner.nextLine().trim();

            if (username.isEmpty()) {
                username = "Guest";
            }

            // Check for admin authentication
            System.out.print(colorize(ANSI_YELLOW, "Admin password (optional, press Enter to skip): "));
            String adminPassword = scanner.nextLine().trim();

            if (!adminPassword.isEmpty()) {
                username += ":admin:" + adminPassword;
                printInfo("Attempting admin authentication...");
            }

            // Send authentication request
            out.println("AUTH:" + username);
        } else {
            // Re-authentication with existing user
            out.println("AUTH:" + currentUser.getUsername());
        }

        // Wait for response
        String response = in.readLine();
        if (response == null) {
            throw new IOException("Server closed connection during authentication");
        }

        if (response.startsWith("AUTH_SUCCESS:")) {
            String[] parts = response.substring(13).split(",");
            if (parts.length == 2) {
                if (currentUser == null) {
                    currentUser = new User(parts[0]);
                    currentUser.setUserID(UUID.fromString(parts[1]));
                }

                printSuccess("Authentication successful! Welcome, " + currentUser.getUsername() + "!");
                printInfo("You are in room: " + colorize(ANSI_CYAN, currentRoom));
                printInfo("Type " + colorize(ANSI_GREEN, "/help") + " for available commands");
                System.out.println(colorize(ANSI_BLUE, "═".repeat(50)));
                return true;
            }
        } else if (response.startsWith("ERROR:")) {
            printError("Authentication failed: " + response.substring(6));
            return false;
        }

        throw new IOException("Invalid authentication response");
    }

    private void startMessageListener() {
        messageListener = new Thread(this::listenForMessages);
        messageListener.setDaemon(true);
        messageListener.start();
    }

    private void listenForMessages() {
        try {
            String message;
            while (isRunning.get() && isConnected.get() && (message = in.readLine()) != null) {
                handleIncomingMessage(message);
            }
        } catch (IOException e) {
            if (isRunning.get() && isConnected.get()) {
                printError("Connection lost: " + e.getMessage());
                isConnected.set(false);
            }
        }
    }

    private void handleIncomingMessage(String message) {
        if (message.startsWith("SERVER:")) {
            printServer(message.substring(7));
        } else if (message.startsWith("MSG:")) {
            printMessage(message.substring(4));
        } else if (message.startsWith("PRIVATE:")) {
            printPrivateMessage(message.substring(8));
        } else if (message.startsWith("PRIVATE_SENT:")) {
            printPrivateSent(message.substring(13));
        } else if (message.startsWith("HISTORY:")) {
            printHistory(message.substring(8));
        } else if (message.startsWith("ERROR:")) {
            printError(message.substring(6));
        } else if (message.equals("DISCONNECT")) {
            printWarning("Server requested disconnection");
            isRunning.set(false);
        } else if (message.startsWith("KICKED:")) {
            printError(message.substring(7));
            isRunning.set(false);
        } else {
            printMessage(message);
        }

        if (!message.startsWith("HISTORY:")) {
            System.out.print(getPrompt());
        }
    }

    private void handleUserInput() {
        System.out.print(getPrompt());

        while (isRunning.get() && isConnected.get()) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    System.out.print(getPrompt());
                    continue;
                }

                if (input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/exit")) {
                    break;
                }

                if (input.startsWith("/")) {
                    handleLocalCommand(input);
                } else {
                    sendMessage(input);
                }
            }

            // Small delay to prevent busy waiting
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleLocalCommand(String command) {
        String[] parts = command.substring(1).split(" ", 2);
        String action = parts[0].toLowerCase();

        switch (action) {
            case "help":
                showLocalHelp();
                break;
            case "clear":
                clearScreen();
                break;
            case "time":
                printInfo("Current time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                break;
            case "status":
                showConnectionStatus();
                break;
            case "color":
                toggleColorSupport();
                break;
            case "room":
                if (parts.length > 1) {
                    currentRoom = parts[1];
                    printInfo("Room context updated to: " + colorize(ANSI_CYAN, currentRoom));
                } else {
                    printInfo("Current room: " + colorize(ANSI_CYAN, currentRoom));
                }
                break;
            default:
                // Send as server command
                out.println("CMD:" + command.substring(1));
                return;
        }

        System.out.print(getPrompt());
    }

    private void showLocalHelp() {
        System.out.println(colorize(ANSI_CYAN, "\n═══ Local Commands ═══"));
        System.out.println(colorize(ANSI_GREEN, "/clear") + " - Clear the screen");
        System.out.println(colorize(ANSI_GREEN, "/time") + " - Show current time");
        System.out.println(colorize(ANSI_GREEN, "/status") + " - Show connection status");
        System.out.println(colorize(ANSI_GREEN, "/color") + " - Toggle color support");
        System.out.println(colorize(ANSI_GREEN, "/room [name]") + " - Show/set current room context");
        System.out.println(colorize(ANSI_GREEN, "/quit") + " - Exit the application");

        System.out.println(colorize(ANSI_CYAN, "\n═══ Server Commands ═══"));
        System.out.println(colorize(ANSI_GREEN, "/help") + " - Show server help");
        System.out.println(colorize(ANSI_GREEN, "/users") + " - List users in current room");
        System.out.println(colorize(ANSI_GREEN, "/rooms") + " - List all available rooms");
        System.out.println(colorize(ANSI_GREEN, "/join <room>") + " - Join a chat room");
        System.out.println(colorize(ANSI_GREEN, "/create <room> [description]") + " - Create a new room");
        System.out.println(colorize(ANSI_GREEN, "/msg <user> <message>") + " - Send private message");
        System.out.println(colorize(ANSI_GREEN, "/stats") + " - Show server statistics");
        System.out.println(colorize(ANSI_CYAN, "═".repeat(30)));
    }

    private void clearScreen() {
        // ANSI escape code to clear screen
        System.out.print("\033[2J\033[H");
        System.out.flush();
        displayWelcomeMessage();
        printInfo("Screen cleared. You are in room: " + colorize(ANSI_CYAN, currentRoom));
    }

    private void showConnectionStatus() {
        System.out.println(colorize(ANSI_CYAN, "\n═══ Connection Status ═══"));
        System.out.println("Connected: " + (isConnected.get() ? colorize(ANSI_GREEN, "Yes") : colorize(ANSI_RED, "No")));
        System.out.println("Server: " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("Username: " + (currentUser != null ? colorize(ANSI_YELLOW, currentUser.getUsername()) : "Not authenticated"));
        System.out.println("Current Room: " + colorize(ANSI_CYAN, currentRoom));
        System.out.println("Reconnect Attempts: " + reconnectAttempts + "/" + maxReconnectAttempts);
        System.out.println("Color Support: " + (colorSupport ? colorize(ANSI_GREEN, "Enabled") : colorize(ANSI_RED, "Disabled")));
        System.out.println(colorize(ANSI_CYAN, "═".repeat(25)));
    }

    private void toggleColorSupport() {
        colorSupport = !colorSupport;
        printInfo("Color support " + (colorSupport ? "enabled" : "disabled"));
    }

    private void sendMessage(String message) {
        if (currentUser != null && isConnected.get()) {
            out.println("MSG:" + currentUser.getUserID() + ":" + message);
        }
    }

    private String getPrompt() {
        return colorize(ANSI_BLUE, "[" + currentRoom + "] ") +
                colorize(ANSI_YELLOW, currentUser != null ? currentUser.getUsername() : "Guest") +
                colorize(ANSI_BLUE, " > ");
    }

    private void printMessage(String message) {
        System.out.println(colorize(ANSI_GREEN, "[>>] " + message));
    }

    private void printPrivateMessage(String message) {
        System.out.println(colorize(ANSI_PURPLE, "[PM] " + message));
    }

    private void printPrivateSent(String message) {
        System.out.println(colorize(ANSI_PURPLE, "[->] " + message));
    }

    private void printServer(String message) {
        System.out.println(colorize(ANSI_CYAN, "[Server] " + message));
    }

    private void printHistory(String message) {
        System.out.println(colorize(ANSI_BLUE, "[History] " + message));
    }

    private void printError(String message) {
        System.out.println(colorize(ANSI_RED, "[!] Error: " + message));
    }

    private void printWarning(String message) {
        System.out.println(colorize(ANSI_YELLOW, "[W] Warning: " + message));
    }

    private void printSuccess(String message) {
        System.out.println(colorize(ANSI_GREEN, "[OK] " + message));
    }

    private void printInfo(String message) {
        System.out.println(colorize(ANSI_BLUE, "[i] " + message));
    }

    private String colorize(String color, String text) {
        return colorSupport ? color + text + ANSI_RESET : text;
    }

    private void shutdown() {
        printInfo("Shutting down client...");
        isRunning.set(false);
        isConnected.set(false);

        // Send disconnect message
        if (out != null) {
            out.println("DISCONNECT");
        }

        // Close resources
        closeQuietly(scanner);
        closeQuietly(in);
        closeQuietly(out);
        closeQuietly(socket);

        // Wait for listener thread to finish
        if (messageListener != null && messageListener.isAlive()) {
            try {
                messageListener.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println(colorize(ANSI_GREEN, "Goodbye! Thanks for using Messengr!"));
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Ignore exceptions during cleanup
            }
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore exceptions during cleanup
            }
        }
    }
}