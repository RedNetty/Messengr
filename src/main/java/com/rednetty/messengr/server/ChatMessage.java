package com.rednetty.messengr.server;

import java.time.LocalDateTime;

public class ChatMessage {
    private final String content;
    private final LocalDateTime timestamp;
    private final String roomName;

    public ChatMessage(String content, LocalDateTime timestamp, String roomName) {
        this.content = content;
        this.timestamp = timestamp;
        this.roomName = roomName;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getRoomName() {
        return roomName;
    }
}
