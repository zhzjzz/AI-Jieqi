package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class AiGameClient {
    private final String host;
    private final int port;
    private final AiAgent agent;
    private final AiDecisionLogger logger;
    private GameBoard board;
    private Piece.Side mySide;
    private boolean inCheck;

    public AiGameClient(String host, int port, AiAgent agent, AiDecisionLogger logger) {
        this.host = host;
        this.port = port;
        this.agent = agent;
        this.logger = logger;
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            while (true) {
                Object obj = input.readObject();
                if (obj instanceof Move move) {
                    applyMove(move);
                    continue;
                }
                String message = String.valueOf(obj);
                if (message.startsWith("COLOR:")) {
                    mySide = "RED".equals(message.substring("COLOR:".length())) ? Piece.Side.RED : Piece.Side.BLACK;
                } else if (message.startsWith("SEED:")) {
                    board = new GameBoard(Long.parseLong(message.substring("SEED:".length())));
                } else if (message.startsWith("CHECK:")) {
                    inCheck = Boolean.parseBoolean(message.substring("CHECK:".length()));
                } else if (message.startsWith("YOUR_TURN:") && Boolean.parseBoolean(message.substring("YOUR_TURN:".length()))) {
                    AiDecision decision = agent.chooseMove(board, mySide, inCheck);
                    decision.move().setTurnStartTime(System.currentTimeMillis());
                    logger.log(decision);
                    output.writeObject(decision.move());
                    output.flush();
                } else if (message.startsWith("WINNER:")) {
                    System.out.println(message);
                    return;
                } else if (message.startsWith("ERROR:")) {
                    System.out.println(message);
                }
            }
        } catch (Exception e) {
            System.out.println("AI client ended: " + e.getMessage());
        }
    }

    private void applyMove(Move move) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        if (source == null) {
            return;
        }
        if (sr == dr && sc == dc) {
            source.setRevealed(true);
            if (move.getType() != null) {
                source.setType(move.getType());
            }
            return;
        }
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        source.setRevealed(true);
        if (move.getType() != null) {
            source.setType(move.getType());
        }
    }
}
