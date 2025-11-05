# Une Application de Chat Multi-utilisateurs

Développée avec Java Sockets & JavaFX



# Vue d'ensemble du projet

## Objectif:

Créer une application de chat multi-utilisateurs en temps réel à partir de zéro.

## Technologies principales

### Réseau (Backend)

**Java Sockets** pour une communication TCP bidirectionnelle de bas niveau.

### Interface Utilisateur (Frontend)

**JavaFX** pour une interface graphique (GUI) moderne et réactive.

## Fonctionnalités clés

- Architecture Client-Serveur.
- Messagerie en temps réel.

- Gestion de plusieurs clients simultanément.

- Liste en direct de tous les utilisateurs en ligne.

- Messages système pour les connexions/déconnexions des utilisateurs.



# Architecture de haut niveau

Cette application utilise un **Modèle Client-Serveur** classique.

![Différence entre les réseaux client-serveur et peer-to-peer - WayToLearnX](https://3.bp.blogspot.com/-T4rIjHkqgTU/W1mz323Y1mI/AAAAAAAAB7I/CFBj6FF53gcUUaxIN80JOaceiVPZ9HUmACLcBGAs/s640/R%25C3%25A9seau%2Bclient-serveur.png)

## Le Serveur (`ChatServer`) :

- Agit comme le "hub" central ou la "tour de contrôle".
- Il écoute les nouvelles connexions de clients.
- Il maintient la liste de tous les utilisateurs connectés.
- Il reçoit les messages d'un client et les **diffuse** (broadcast) à tous les autres clients.



## Les Clients (`ChatUI` / `ChatClient`) :

- Chaque utilisateur exécute une application client.
- Les clients se connectent *uniquement* au serveur.
- Les clients ne se parlent **pas** directement entre eux.
- Ils envoient des messages *au* serveur et écoutent les messages *provenant* du serveur.



# Le Protocole de Données (`Message.java`)

Avant d'examiner le serveur ou le client, nous devons définir *comment* ils communiquent. Nous ne pouvons pas envoyer simplement du texte brut, car le serveur a besoin de connaître le *but* du message.

**Problème :** "Alice" est-il un message texte, ou est-ce le nom d'utilisateur d'un nouvel utilisateur qui se connecte ?

**Solution :** Nous avons créé une classe `Message` personnalisée qui implémente `Serializable`. Cela permet à Java d'envoyer facilement cet objet sur le réseau.

Nous utilisons une **`enum`** pour définir un protocole clair.

## Exemple de code (`Message.java`) :

```java
public class Message implements Serializable {
    // Définit *l'intention* du message
    public enum Type {
        CONNECT,     // Le client se connecte
        DISCONNECT,  // Le client se déconnecte
        TEXT,        // Un message texte normal
        USER_LIST,   // Le serveur envoie la liste des utilisateurs
        USER_JOINED, // Le serveur annonce un nouvel utilisateur
        USER_LEFT    // Le serveur annonce qu'un utilisateur est parti
    }

    private Type type;
    private String sender;
    private String content;
    // ...
}
```



# Le Serveur : Accepter les clients (`ChatServer.java`)

Le premier travail du serveur est d'écouter les nouvelles connexions.

1. Il crée un `ServerSocket` sur un port spécifique.
2. Il exécute une boucle infinie `while(true)` pour `accept()` constamment de nouvelles connexions.
3. **Point crucial :** Lorsqu'un nouveau `Socket` (client) se connecte, le serveur **crée immédiatement un nouveau `ClientHandler` sur un nouveau `Thread`** pour le gérer.

Ce multi-threading est la *clé* pour gérer de nombreux utilisateurs en même temps.

**Exemple de code** (`ChatServer.java - main`) :

```java
public class ChatServer {
    // ...
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(...)) {
            while (true) {
                // Cette ligne est bloquante jusqu'à ce qu'un client se connecte
                Socket clientSocket = serverSocket.accept();
                
                // Déléguer ce nouveau client à son propre thread
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            // ...
        }
    }
}
```



# Le Serveur : Diffusion des messages (`ChatServer.java`)

Le deuxième travail du serveur est de gérer la communication.

- Nous utilisons une `ConcurrentHashMap` pour stocker tous les clients connectés. Cette `Map` est **thread-safe** (sécurisée pour les threads), ce qui est essentiel car de nombreux threads `ClientHandler` y accéderont en même temps.
- Lorsqu'un thread `ClientHandler` reçoit un message `TEXT` de son client, il appelle la méthode `broadcast()` du serveur principal.
- La méthode `broadcast()` boucle alors sur tous les *autres* clients dans la `Map` et leur transmet le message.

**Exemple de code** (`ChatServer.java - broadcast/add`) :

```java
// Une Map thread-safe : Clé=username, Valeur=le handler du client
private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

// Appelé par un thread ClientHandler
public static void broadcast(Message message, String excludeUser) {
    // Boucler sur chaque entrée de notre map de clients
    for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
        
        // Ne pas renvoyer le message à l'expéditeur
        if (!entry.getKey().equals(excludeUser)) {
            entry.getValue().sendMessage(message);
        }
    }
}

// Quand un nouveau client est ajouté, on informe tout le monde
public static void addClient(String username, ClientHandler handler) {
    clients.put(username, handler);
    broadcastUserList(); // Mettre à jour la liste d'utilisateurs de tout le monde
}
```



# Le Client : Connexion (`ChatClient.java`)

Le côté client est divisé en deux classes :

1. `ChatClient` : La logique "backend" pour le réseau.
2. `ChatUI` : La fenêtre JavaFX "frontend".

La classe `ChatClient` se connecte au serveur et démarre un **nouveau thread** juste pour l'écoute.

**Problème :** Si nous écoutons les messages sur le thread principal de JavaFX, toute l'application va **se bloquer** (geler) en attendant le serveur.

**Solution :** Créer un thread d'arrière-plan (`listenForMessages`) dont le seul travail est d'attendre les messages entrants.

**Exemple de code** (`ChatClient.java - connect`) :

```java
public class ChatClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Consumer<Message> messageHandler; // Un "callback" vers l'UI

    public boolean connect(String host, int port, ..., Consumer<Message> handler) {
        this.messageHandler = handler;
        try {
            socket = new Socket(...);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // 1. Indiquer au serveur notre connexion
            sendMessage(new Message(Message.Type.CONNECT, username, ""));

            // 2. Démarrer le thread d'arrière-plan pour écouter les messages
            new Thread(this::listenForMessages).start();
            
            return true;
        } // ...
    }
}
```



# L'UI et le Défi du Threading (`ChatUI.java`)

C'est le défi le plus critique : **Comment le thread d'arrière-plan met-il à jour l'interface utilisateur ?**

1. Le thread d'arrière-plan du `ChatClient` (de la diapositive précédente) reçoit un objet `Message`.

2. **Problème :** Dans JavaFX, seul le **thread principal de l'application JavaFX** est autorisé à modifier l'interface utilisateur (comme ajouter du texte à `messageArea`). Si notre thread d'arrière-plan essaie de le faire, l'application plantera.

3. **Solution :** `Platform.runLater()`.

   - Notre thread d'arrière-plan reçoit le message.
   - Il enveloppe le code de mise à jour de l'UI dans un `Platform.runLater(...)`.
   - Cela "planifie" l'exécution de ce code en toute sécurité sur le thread principal de JavaFX.

   **Exemple de code** (Le lien entre `ChatClient` et `ChatUI`) :

   ```java
   // Dans ChatClient.java (S'exécute sur le THREAD D'ARRIÈRE-PLAN)
   private void listenForMessages() {
       try {
           while (connected) {
               // Cette ligne BLOQUE, en attente d'un message
               Message message = (Message) in.readObject();
   
               // Nous avons un message !
               // Maintenant, planifions la mise à jour de l'UI sur le thread PRINCIPAL
               Platform.runLater(() -> {
                   if (messageHandler != null) {
                       messageHandler.accept(message);
                   }
               });
           }
       } // ...
   }
   
   // Dans ChatUI.java (S'exécute sur le THREAD JAVAFX PRINCIPAL)
   private void handleMessage(Message message) {
       // Ce code est maintenant SÉCURISÉ à exécuter !
       switch (message.getType()) {
           case TEXT:
               displayMessage(message.getSender(), message.getContent(), ...);
               break;
           case USER_LIST:
               updateUserList(message.getContent());
               break;
           // ...
       }
   }
   ```

   # Démonstration en direct







