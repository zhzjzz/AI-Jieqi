package org.example.client;

import org.example.ui.GameApp;

import javafx.application.Application;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        try {
            Socket socket = new Socket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            String colorMessage = String.valueOf(in.readObject());
            String seedMessage = String.valueOf(in.readObject());
            String color = colorMessage.contains("RED") ? "RED" : "BLACK";
            long seed = Long.parseLong(seedMessage.substring(seedMessage.indexOf(':') + 1));
            GameApp.configure(socket, in, out, color, seed);
            Application.launch(GameApp.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
