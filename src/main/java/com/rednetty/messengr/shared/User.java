package com.rednetty.messengr.shared;

import java.util.UUID;

public class User {
    private String username;
    private UUID userID;

    public User(String username) {
        this.username = username;
        this.userID = UUID.randomUUID();
    }

    public UUID getUserID() {
        return userID;
    }

    public void setUserID(UUID userID) {
        this.userID = userID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}