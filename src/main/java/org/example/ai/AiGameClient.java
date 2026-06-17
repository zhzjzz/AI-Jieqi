package org.example.ai;

import com.google.gson.JsonObject;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.protocol.JsonProtocol;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public class AiGameClient {
    private final String host;
    private final int port;
    private final AiAgent agent;
    private final AiDecisionLogger logger;
    private final CountDownLatch done = new CountDownLatch(1);
    private WebSocketClient client;
    private GameBoard board;
    private Piece.Side mySide;
    private boolean gameOver;

    public AiGameClient(String host, int port, AiAgent agent, AiDecisionLogger logger) {
        this.host = host;
        this.port = port;
        this.agent = agent;
        this.logger = logger;
    }

    public void start() {
        try {
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    send(JsonProtocol.message("startMatch").toString());
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    done.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println("AI client error: " + ex.getMessage());
                    done.countDown();
                }
            };
            client.connectBlocking();
            done.await();
        } catch (Exception e) {
            System.out.println("AI client ended: " + e.getMessage());
        }
    }

    private void handleMessage(String message) {
        JsonObject json;
        try {
            json = JsonProtocol.parse(message);
        } catch (Exception e) {
            System.out.println("Invalid JSON from server: " + message);
            return;
        }
        switch (JsonProtocol.typeOf(json)) {
            case "gameStart" -> handleGameStart(json);
            case "moveResult" -> handleMoveResult(json);
            case "gameOver", "timeout" -> {
                gameOver = true;
                System.out.println(json);
                done.countDown();
            }
            case "error" -> System.out.println(json);
            default -> {
            }
        }
    }

    private void handleGameStart(JsonObject json) {
        mySide = JsonProtocol.sideFromColor(JsonProtocol.string(json, "yourColor", "black"));
        board = JsonProtocol.boardFromInitial(json.getAsJsonArray("initialBoard"));
        if (JsonProtocol.bool(json, "firstHand", false)) {
            chooseAndSendMove();
        }
    }

    private void handleMoveResult(JsonObject json) {
        if (!JsonProtocol.bool(json, "success", false) || !JsonProtocol.bool(json, "valid", false)) {
            if (!gameOver) {
                chooseAndSendMove();
            }
            return;
        }
        Move move = JsonProtocol.toMove(json.getAsJsonObject("move"));
        if (json.has("flipResult")) {
            move.setType(JsonProtocol.pieceType(json.get("flipResult").getAsString()));
        }
        Piece.Side movedSide = sideAt(move.getSource());
        applyMove(move);
        Piece.Side nextSide = movedSide == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
        if (nextSide == mySide && !gameOver) {
            chooseAndSendMove();
        }
    }

    private void chooseAndSendMove() {
        if (board == null || mySide == null || client == null || !client.isOpen()) {
            return;
        }
        try {
            AiDecision decision = agent.chooseMove(board, mySide, false);
            decision.move().setTurnStartTime(System.currentTimeMillis());
            logger.log(decision);
            client.send(JsonProtocol.moveMessage(decision.move()).toString());
        } catch (Exception e) {
            System.out.println("AI failed to choose move: " + e.getMessage());
        }
    }

    private Piece.Side sideAt(String coord) {
        int row = board.rowFromCoord(coord);
        int col = board.colFromCoord(coord);
        Piece piece = board.get(row, col);
        return piece == null ? mySide : piece.getSide();
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
        source.setRevealed(true);
        if (move.getType() != null) {
            source.setType(move.getType());
        }
        if (sr != dr || sc != dc) {
            board.set(dr, dc, source);
            board.set(sr, sc, null);
        }
    }
}
