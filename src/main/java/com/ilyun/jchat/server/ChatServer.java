package com.ilyun.jchat.server;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.ilyun.jchat.Message;

public class ChatServer {
    private static final int PORT = 5000;
    private static final int BACKLOG = 50;
    private static final String INET_ADDRESS = "192.168.11.113";
    private static final InetAddress ADDRESS;

    static {
        try {
            ADDRESS = InetAddress.getByName(INET_ADDRESS);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== Chat Server Started on Port " + PORT + " ===\n");

        try (ServerSocket serverSocket = new ServerSocket(PORT, BACKLOG, ADDRESS)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // Broadcast message to all clients except sender
    public static void broadcast(Message message, String excludeUser) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUser)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    // Send message to specific user
    public static void sendToUser(String username, Message message) {
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    // Add client
    public static void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        System.out.println("✓ " + username + " connected. Total users: " + clients.size());

        // Send updated user list to all clients
        broadcastUserList();

        // Notify others that user joined
        broadcast(new Message(Message.Type.USER_JOINED, "Server", username), username);
    }

    // Remove client
    public static void removeClient(String username) {
        clients.remove(username);
        System.out.println("✗ " + username + " disconnected. Total users: " + clients.size());

        // Send updated user list to all clients
        broadcastUserList();

        // Notify others that user left
        broadcast(new Message(Message.Type.USER_LEFT, "Server", username), null);
    }

    // Send list of online users to all clients
    private static void broadcastUserList() {
        String userList = String.join(",", clients.keySet());
        Message listMessage = new Message(Message.Type.USER_LIST, "Server", userList);

        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(listMessage);
        }
    }


    // ============================================
    // Inner Class: ClientHandler
    // ============================================
    static class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Initialize streams
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Wait for CONNECT message with username
                Message connectMsg = (Message) in.readObject();
                if (connectMsg.getType() == Message.Type.CONNECT) {
                    username = connectMsg.getSender();

                    // Check if username already exists
                    if (clients.containsKey(username)) {
                        sendMessage(new Message(Message.Type.DISCONNECT, "Server",
                                "Username already taken"));
                        socket.close();
                        return;
                    }

                    addClient(username, this);

                    // Main message loop
                    while (true) {
                        Message message = (Message) in.readObject();

                        if (message.getType() == Message.Type.DISCONNECT) {
                            break;
                        } else if (message.getType() == Message.Type.TEXT) {
                            System.out.println(username + ": " + message.getContent());
                            // Broadcast to all other clients
                            broadcast(message, username);
                        }
                    }
                }

            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Connection lost: " + username);
            } finally {
                cleanup();
            }
        }

        public void sendMessage(Message message) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("Error sending to " + username);
            }
        }

        private void cleanup() {
            if (username != null) {
                removeClient(username);
            }
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
