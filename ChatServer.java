import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int DEFAULT_PORT = 12345;
    private static final String EXIT_COMMAND = "/bye";
    private static final String LIST_COMMAND = "/list";
    private static final String CREATE_COMMAND = "/create";
    private static final String JOIN_COMMAND = "/join";
    private static final String EXIT_ROOM_COMMAND = "/exit";
    private static final String USERS_COMMAND = "/users";
    private static final String ROOM_USERS_COMMAND = "/roomusers";
    private static final String WHISPER_COMMAND = "/whisper";
    private static final String INVITE_COMMAND = "/invite";
    private static final String CHAT_HISTORY_DIRECTORY = "chat_history";

    private static Set<String> nicknames = new HashSet<>();
    private static Map<Integer, ChatRoom> chatRooms = new HashMap<>();
    private static Map<String, PrintWriter> clients = new HashMap<>();
    private static int nextRoomId = 1;

    public static void main(String[] args) {
        createChatHistoryDirectory();
        try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)) {
            System.out.println("Server started on port " + DEFAULT_PORT + ".");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("A new client connected: " + clientSocket.getInetAddress());
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nickname;
        private ChatRoom currentRoom;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("Please enter your nickname: ");
                nickname = in.readLine();
                if (nickname == null || nickname.trim().isEmpty()) {
                    nickname = "Anonymous" + UUID.randomUUID().toString().substring(0, 8);
                }
                synchronized (nicknames) {
                    while (nicknames.contains(nickname)) {
                        out.println("Nickname already in use. Please enter a different nickname: ");
                        nickname = in.readLine();
                    }
                    nicknames.add(nickname);
                }
                System.out.println(nickname + " connected.");
                clients.put(nickname, out);

                // Exchange messages with the client
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.equals(EXIT_COMMAND)) {
                        break;
                    } else if (inputLine.equals(USERS_COMMAND)) {
                        listUsers();
                    } else if (inputLine.equals(ROOM_USERS_COMMAND)) {
                        listRoomUsers();
                    } else if (inputLine.startsWith(WHISPER_COMMAND)) {
                        sendWhisper(inputLine);
                    } else if (inputLine.startsWith(INVITE_COMMAND)) {
                        inviteUser(inputLine);
                    } else {
                        processInput(inputLine);
                    }
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            } finally {
                if (nickname != null) {
                    nicknames.remove(nickname);
                    clients.remove(nickname);
                    System.out.println(nickname + " disconnected.");
                    if (currentRoom != null) {
                        currentRoom.removeClient(this);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }

        private void processInput(String inputLine) {
            // Handle client input
            if (inputLine.startsWith(CREATE_COMMAND)) {
                createChatRoom();
            } else if (inputLine.equals(LIST_COMMAND)) {
                listChatRooms();
            } else if (inputLine.startsWith(JOIN_COMMAND)) {
                joinChatRoom(inputLine);
            } else if (inputLine.equals(EXIT_ROOM_COMMAND)) {
                exitChatRoom();
            } else {
                // Send message to current room
                if (currentRoom != null) {
                    currentRoom.broadcastMessage(nickname + ": " + inputLine);
                    saveChatHistory(nickname + ": " + inputLine);
                }
            }
        }

        private void createChatRoom() {
            int roomId = nextRoomId++;
            ChatRoom room = new ChatRoom(roomId);
            chatRooms.put(roomId, room);
            out.println("Room " + roomId + " created.");
            joinChatRoom(JOIN_COMMAND + " " + roomId);
        }

        private void listChatRooms() {
            out.println("Current chat rooms:");
            for (int roomId : chatRooms.keySet()) {
                out.println("Room ID: " + roomId);
            }
        }

        private void joinChatRoom(String inputLine) {
            String[] parts = inputLine.split("\\s+", 2);
            if (parts.length == 2) {
                int roomId = Integer.parseInt(parts[1]);
                ChatRoom room = chatRooms.get(roomId);
                if (room != null) {
                    if (currentRoom != null) {
                        currentRoom.removeClient(this);
                    }
                    currentRoom = room;
                    currentRoom.addClient(this);
                    out.println("Joined the room.");
                } else {
                    out.println("Room ID does not exist.");
                }
            } else {
                out.println("Please enter the room ID.");
            }
        }

        private void exitChatRoom() {
            if (currentRoom != null) {
                currentRoom.removeClient(this);
                currentRoom = null;
                out.println("Moved to the lobby.");
            } else {
                out.println("Not currently in a room.");
            }
        }

        private void listUsers() {
            out.println("Current users:");
            for (String user : nicknames) {
                out.println(user);
            }
        }

        private void listRoomUsers() {
            if (currentRoom != null) {
                out.println("Users in the current room:");
                for (ClientHandler client : currentRoom.getClients()) {
                    out.println(client.getNickname());
                }
            } else {
                out.println("Not currently in a room.");
            }
        }

        private void sendWhisper(String inputLine) {
            String[] parts = inputLine.split("\\s+", 3);
            if (parts.length == 3) {
                String recipient = parts[1];
                String message = parts[2];
                PrintWriter recipientOut = clients.get(recipient);
                if (recipientOut != null) {
                    recipientOut.println("[Whisper from " + nickname + "]: " + message);
                } else {
                    out.println("User " + recipient + " not found or not online.");
                }
            } else {
                out.println("Invalid whisper command. Usage: /whisper [recipient] [message]");
            }
        }

        private void inviteUser(String inputLine) {
            String[] parts = inputLine.split("\\s+", 2);
            if (parts.length == 2) {
                String invitee = parts[1];
                PrintWriter inviteeOut = clients.get(invitee);
                if (inviteeOut != null) {
                    inviteeOut.println("You have been invited to join the room by " + nickname);
                    inviteeOut.println("invited");
                } else {
                    out.println("User " + invitee + " not found or not online.");
                }
            } else {
                out.println("Invalid invite command. Usage: /invite [nickname]");
            }
        }

        private void saveChatHistory(String message) {
            try {
                String filename = CHAT_HISTORY_DIRECTORY + "/" + currentRoom.getRoomId() + ".txt";
                FileWriter writer = new FileWriter(filename, true);
                writer.write(message + "\n");
                writer.close();
            } catch (IOException e) {
                System.err.println("Error saving chat history: " + e.getMessage());
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public String getNickname() {
            return nickname;
        }
    }

    private static class ChatRoom {
        private int roomId;
        private List<ClientHandler> clients = new ArrayList<>();

        public ChatRoom(int roomId) {
            this.roomId = roomId;
        }

        public synchronized void addClient(ClientHandler client) {
            clients.add(client);
        }

        public synchronized void removeClient(ClientHandler client) {
            clients.remove(client);
            if (clients.isEmpty()) {
                chatRooms.remove(roomId);
                System.out.println("Room " + roomId + " deleted.");
            }
        }

        public synchronized void broadcastMessage(String message) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }

        public synchronized List<ClientHandler> getClients() {
            return new ArrayList<>(clients);
        }

        public int getRoomId() {
            return roomId;
        }
    }

    private static void createChatHistoryDirectory() {
        File directory = new File(CHAT_HISTORY_DIRECTORY);
        if (!directory.exists()) {
            if (directory.mkdir()) {
                System.out.println("Chat history directory created.");
            } else {
                System.err.println("Failed to create chat history directory.");
            }
        }
    }
}
