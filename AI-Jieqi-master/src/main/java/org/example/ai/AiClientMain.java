package org.example.ai;

public class AiClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        new AiGameClient(host, port, new AiAgent(AiConfig.load(), new HttpDeepSeekClient()), new AiDecisionLogger(System.out)).start();
    }
}
