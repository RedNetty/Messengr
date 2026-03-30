# Messengr

**A multi-user TCP chat application built in Java.**

Messengr is a real-time chat system with support for multiple chat rooms, private messaging, and admin controls. Built to demonstrate socket programming, multithreading, and client-server architecture in Java.

## Features

- **Multi-user support** — Multiple clients can connect simultaneously
- **Chat rooms** — Create and join named rooms for organized discussions
- **Private messaging** — Direct messages between individual users
- **Real-time communication** — Messages delivered instantly across all clients
- **Admin commands** — User management (kick, view stats)
- **Auto-reconnection** — Client automatically reconnects on dropped connections
- **Message history** — Shows recent messages when joining a room
- **Configurable** — Server behavior controlled via `server.properties`

## Technical Implementation

### Architecture
- **Client-Server model** using TCP sockets
- **Thread-per-client** design with an `ExecutorService` thread pool
- **Concurrent data structures** (`ConcurrentHashMap`, `AtomicBoolean`) for thread safety
- **Custom protocol** for routing different message types (room messages, private messages, commands, system notifications)

### Key Java Concepts
- Socket programming (`ServerSocket` / `Socket`)
- Multithreading with `ExecutorService`
- Concurrent collections
- Resource management with try-with-resources
- Graceful shutdown handling

## Getting Started

### Prerequisites
- Java 8+

### Running the Application

**1. Start the server:**
```bash
mvn clean package
java -cp target/messengr.jar com.rednetty.messengr.server.Server
```

**2. Connect a client (in a new terminal):**
```bash
java -cp target/messengr.jar com.rednetty.messengr.client.ClientApp
```

**3. Connect additional clients** by opening more terminals and repeating step 2.

### Configuration

Edit `src/main/resources/server.properties` to adjust server settings:

```properties
server.port=7234
server.max_clients=100
security.admin_password=${ADMIN_PASSWORD:changeme}
```

> Set the `ADMIN_PASSWORD` environment variable before starting the server to configure the admin password securely.

## Chat Commands

| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/users` | List users in current room |
| `/rooms` | List all available rooms |
| `/join <room>` | Join a room |
| `/create <room>` | Create a new room |
| `/msg <user> <message>` | Send a private message |
| `/status` | Show connection status |
| `/quit` | Exit |

Admin commands (require server password):

| Command | Description |
|---------|-------------|
| `/kick <user>` | Remove a user from the server |
| `/stats` | View server statistics |

## Project Structure

```
src/main/java/com/rednetty/messengr/
├── client/
│   └── ClientApp.java      # Terminal-based chat client
├── server/
│   ├── Server.java         # Main server (accepts connections, manages rooms/users)
│   ├── ClientHandler.java  # Per-client thread: reads/routes messages
│   ├── ChatRoom.java       # Room state and broadcast logic
│   └── ChatMessage.java    # Message data model
└── shared/
    └── User.java           # Shared user model
```

## Known Limitations
- Server runs on localhost; remote connections require firewall configuration
- No persistent message storage (history is in-memory only)
- Basic authentication (password-based admin access)

---

Built as a deep dive into concurrent network programming in Java.
