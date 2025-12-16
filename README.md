# Multi-User Chat Application

A Java-based chat system that supports multiple users, chat rooms, and private messaging. Built to demonstrate network programming, multithreading, and client-server architecture concepts.

## Features

- **Multi-user support** - Multiple clients can connect simultaneously
- **Chat rooms** - Create and join different rooms for organized discussions
- **Private messaging** - Send direct messages between users
- **Real-time communication** - Messages appear instantly across all clients
- **Admin commands** - Basic user management capabilities
- **Auto-reconnection** - Client automatically reconnects if connection is lost
- **Message history** - Shows recent messages when joining a room

## Technical Implementation

### Architecture
- **Client-Server model** using TCP sockets
- **Thread-per-client** design with connection pooling
- **Concurrent data structures** for thread safety
- **Custom protocol** for different message types

### Key Java Concepts Used
- Socket programming for network communication
- Multithreading with ExecutorService and thread pools
- Concurrent collections (ConcurrentHashMap, AtomicBoolean)
- Proper resource management and cleanup
- Exception handling and error recovery

### Server Features
- Handles up to 100 concurrent connections
- Thread-safe user and room management
- Automatic cleanup of inactive connections
- Configurable settings via properties file

## Getting Started

### Prerequisites
- Java 8 or higher

### Running the Application

1. **Start the server:**
   ```bash
   javac Server.java
   java Server
   ```

2. **Run the client (in a new terminal):**
   ```bash
   javac ClientApp.java
   java ClientApp
   ```

3. **Connect multiple clients** by running the client command in additional terminals

## Usage

### Basic Commands
- `/help` - Show available commands
- `/users` - List users in current room
- `/rooms` - List all available rooms
- `/join <room>` - Join a specific room
- `/create <room>` - Create a new room
- `/msg <user> <message>` - Send private message
- `/quit` - Exit the application

### Admin Commands
- `/kick <user>` - Remove a user (admin only)
- `/stats` - Show server statistics

*To get admin privileges, use the password "password123" when prompted during login*

## Project Structure

```
src/
├── com/rednetty/messengr/
│   ├── server/
│   │   └── Server.java          # Main server logic
│   ├── client/
│   │   └── ClientApp.java       # Client application
│   └── shared/
│       └── User.java            # User data model
```

## Known Issues
- Server only runs on localhost currently
- No persistent message storage
- Basic authentication system

## Future Improvements
- Add database storage for messages and users
- Implement file sharing capabilities
- Add web-based client interface
- Improve security with proper authentication
- Add message encryption

---

Built as a learning project to explore network/socket programming and concurrent system design in Java.