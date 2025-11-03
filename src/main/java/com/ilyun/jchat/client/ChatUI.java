package com.ilyun.jchat.client;

// ============================================
// ChatUI.java - FIXED VERSION
// ============================================


import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import com.ilyun.jchat.Message;

public class ChatUI extends Application {
    private ChatClient client;
    private TextArea messageArea;
    private TextField inputField;
    private ListView<String> userList;
    private Label statusLabel;
    private String username;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        primaryStage.setTitle("Simple Chat App");

        // Show login dialog FIRST
        Optional<String> result = showLoginDialog();
        if (!result.isPresent() || result.get().trim().isEmpty()) {
            System.out.println("No username provided. Exiting...");
            Platform.exit();
            return;
        }

        username = result.get().trim();
        System.out.println("Username: " + username);

        // Create main UI
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f0f0;");

        // Left panel - Online Users
        VBox leftPanel = createLeftPanel();

        // Center - Chat area
        VBox centerPanel = createCenterPanel();

        // Bottom - Input area
        HBox bottomPanel = createBottomPanel();

        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // NOW connect to server (after UI is ready)
        connectToServer();

        // Handle window close
        primaryStage.setOnCloseRequest(e -> {
            if (client != null) {
                client.disconnect();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private Optional<String> showLoginDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Join Chat");
        dialog.setHeaderText("Enter your username to join the chat");

        ButtonType loginButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(250);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
                new Label("Username:"),
                usernameField,
                new Label("Server: localhost:5000")
        );
        dialog.getDialogPane().setContent(content);

        Platform.runLater(usernameField::requestFocus);

        // Disable connect button if username is empty
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        connectButton.setDisable(true);

        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            connectButton.setDisable(newValue.trim().isEmpty());
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return usernameField.getText().trim();
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(200);
        panel.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;");

        Label title = new Label("Online Users");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        userList = new ListView<>();
        userList.setStyle("-fx-background-color: white;");
        VBox.setVgrow(userList, Priority.ALWAYS);

        panel.getChildren().addAll(title, userList);
        return panel;
    }

    private VBox createCenterPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: white;");

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5));
        header.setStyle("-fx-background-color: #25D366; -fx-background-radius: 5;");

        Label titleLabel = new Label("Chat Room");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        statusLabel = new Label("Connecting...");
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setFont(Font.font(12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, spacer, statusLabel);

        // Message area
        messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setFont(Font.font("System", 13));
        messageArea.setStyle("-fx-control-inner-background: #f9f9f9;");
        VBox.setVgrow(messageArea, Priority.ALWAYS);

        panel.getChildren().addAll(header, messageArea);
        return panel;
    }

    private HBox createBottomPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(10));
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.setFont(Font.font(14));
        inputField.setPrefHeight(40);
        inputField.setDisable(true); // Disabled until connected
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendButton = new Button("Send");
        sendButton.setPrefHeight(40);
        sendButton.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-font-weight: bold;");
        sendButton.setDisable(true); // Disabled until connected
        sendButton.setOnAction(e -> sendMessage());

        inputField.setOnAction(e -> sendMessage());

        panel.getChildren().addAll(inputField, sendButton);
        return panel;
    }

    private void connectToServer() {
        String host = "localhost";
        int port = 5000;

        System.out.println("Attempting to connect to " + host + ":" + port + " as " + username);

        // Initialize client
        client = new ChatClient();

        // Try to connect
        boolean success = client.connect(host, port, username, this::handleMessage);

        if (success) {
            System.out.println("Connected successfully!");
            updateStatus("Connected");
            inputField.setDisable(false);
            ((Button) inputField.getParent().getChildrenUnmodifiable().get(1)).setDisable(false);
            displaySystemMessage("Connected to chat server");
        } else {
            System.err.println("Connection failed!");
            updateStatus("Connection failed");
            showAlert("Connection Error",
                    "Could not connect to server at " + host + ":" + port +
                            "\n\nMake sure the server is running first!\n\n" +
                            "To start server:\njava -cp bin com.ilyun.jchat.server.ChatServer");
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case TEXT:
                displayMessage(message.getSender(), message.getContent(), message.getTimestamp());
                break;

            case USER_LIST:
                updateUserList(message.getContent());
                break;

            case USER_JOINED:
                displaySystemMessage(message.getContent() + " joined the chat");
                break;

            case USER_LEFT:
                displaySystemMessage(message.getContent() + " left the chat");
                break;

            case DISCONNECT:
                updateStatus("Disconnected");
                inputField.setDisable(true);
                ((Button) inputField.getParent().getChildrenUnmodifiable().get(1)).setDisable(true);
                showAlert("Disconnected", message.getContent());
                break;
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && client != null) {
            client.sendText(text);
            displayMessage("You", text, System.currentTimeMillis());
            inputField.clear();
        }
    }

    private void displayMessage(String sender, String content, long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String time = sdf.format(new Date(timestamp));

        String formattedMessage = String.format("[%s] %s: %s\n", time, sender, content);
        messageArea.appendText(formattedMessage);
    }

    private void displaySystemMessage(String content) {
        messageArea.appendText("*** " + content + " ***\n");
    }

    private void updateUserList(String userListStr) {
        if (userListStr == null || userListStr.isEmpty()) {
            return;
        }

        String[] users = userListStr.split(",");
        userList.getItems().clear();
        for (String user : users) {
            if (!user.trim().isEmpty()) {
                userList.getItems().add("🟢 " + user.trim());
            }
        }
    }

    private void updateStatus(String status) {
        statusLabel.setText(status);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}


// ============================================
// DEBUGGING CHECKLIST
// ============================================
/*

IF YOU SEE "Connection lost: null":

1. CHECK SERVER IS RUNNING:
   - Open terminal/command prompt
   - Run: java -cp bin com.ilyun.jchat.server.ChatServer
   - You should see: "=== Chat Server Started on Port 5000 ==="

2. CHECK PORT 5000 IS FREE:
   - Windows: netstat -ano | findstr :5000
   - Mac/Linux: lsof -i :5000
   - If something is using port 5000, change it in both ChatServer and ChatUI

3. CHECK FIREWALL:
   - Make sure firewall allows Java to use port 5000

4. VERIFY COMPILATION:
   - Make sure Message.java is compiled in both server and client
   - Check bin folder has all .class files

5. RUN ORDER:
   - ALWAYS start server FIRST
   - THEN start client(s)

CONSOLE OUTPUT YOU SHOULD SEE:

SERVER SIDE:
=== Chat Server Started on Port 5000 ===

✓ Alice connected. Total users: 1
Alice: Hello!
✓ Bob connected. Total users: 2
Bob: Hi Alice!

CLIENT SIDE:
Username: Alice
Attempting to connect to localhost:5000 as Alice
Connected successfully!

*/