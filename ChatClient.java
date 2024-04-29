package org.example;

import java.io.*;
import java.net.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Connected to the server.");
            Thread receiveThread = new Thread(new ReceiveMessageThread(in, out));
            receiveThread.start();

            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                if (userInput.equals("/bye")) {
                    break;
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Could not find the server: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    private static class ReceiveMessageThread implements Runnable {
        private BufferedReader in;
        private PrintWriter out;

        public ReceiveMessageThread(BufferedReader in, PrintWriter out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    if (serverMessage.equals("invited")) {
                        System.out.println("You have been invited to join the room.");
                        // 자동으로 채팅방에 참여하기 위해 "/join" 명령어를 서버에 전송
                        out.println("/join");
                    } else {
                        System.out.println(serverMessage);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error receiving messages from the server: " + e.getMessage());
            }
        }
    }
}
