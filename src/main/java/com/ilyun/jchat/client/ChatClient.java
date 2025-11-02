package com.ilyun.jchat.client;

import javafx.application.Platform;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import com.ilyun.jchat.Message;


public class ChatClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private Consumer<Message> messageHandler;
    private boolean connected = false;

    public boolean connect(String host, int port, String username, Consumer<Message> messageHandler) {
        this.username = username;
        this.messageHandler = messageHandler;

        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Send CONNECT message
            sendMessage(new Message(Message.Type.CONNECT, username, ""));

            // Start listening thread
            new Thread(this::listenForMessages).start();

            connected = true;
            return true;

        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }

    private void listenForMessages() {
        try {
            while (connected) {
                Message message = (Message) in.readObject();

                // Handle on JavaFX thread
                Platform.runLater(() -> {
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                });
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                System.err.println("Connection lost: " + e.getMessage());
                Platform.runLater(() -> {
                    if (messageHandler != null) {
                        messageHandler.accept(new Message(Message.Type.DISCONNECT,
                                "Server", "Connection lost"));
                    }
                });
            }
        }
    }

    public void sendMessage(Message message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    public void sendText(String content) {
        sendMessage(new Message(Message.Type.TEXT, username, content));
    }

    public void disconnect() {
        connected = false;
        try {
            sendMessage(new Message(Message.Type.DISCONNECT, username, ""));
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }
}