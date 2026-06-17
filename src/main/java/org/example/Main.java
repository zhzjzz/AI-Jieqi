package org.example;

import org.example.client.GameClient;
import org.example.net.GameServer;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Use: server | client [host] [port]");
            return;
        }
        if ("server".equalsIgnoreCase(args[0])) {
            String port = args.length > 1 ? args[1] : "5000";
            GameServer.main(new String[]{port});
            return;
        }
        if ("client".equalsIgnoreCase(args[0])) {
            String host = args.length > 1 ? args[1] : "127.0.0.1";
            String port = args.length > 2 ? args[2] : "5000";
            GameClient.main(new String[]{host, port});
        }
    }
}
