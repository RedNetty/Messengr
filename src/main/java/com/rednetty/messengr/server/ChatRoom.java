package com.rednetty.messengr.server;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private final String name;
    private final String description;
    private final ConcurrentHashMap<UUID, String> users; // userId -> username
    private final LocalDateTime createdAt;

    public ChatRoom(String name, String description) {
        this.name = name;
        this.description = description;
        this.users = new ConcurrentHashMap<>();
        this.createdAt = LocalDateTime.now();
    }

    public void addUser(UUID userId, String username) {
        users.put(userId, username);
    }

    public void removeUser(UUID userId) {
        users.remove(userId);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ConcurrentHashMap<UUID, String> getUsers() {
        return users;
    }

    public int getUserCount() {
        return users.size();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
