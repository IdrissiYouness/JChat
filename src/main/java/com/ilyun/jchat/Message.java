package com.ilyun.jchat;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        CONNECT,        // Client wants to connect with username
        DISCONNECT,     // Client disconnecting
        TEXT,           // Regular text message
        USER_LIST,      // Server sends list of online users
        USER_JOINED,    // Notify others a user joined
        USER_LEFT       // Notify others a user left
    }

    private Type type;
    private String sender;
    private String content;
    private long timestamp;

    public Message(Type type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender='" + sender + "', content='" + content + "'}";
    }
}