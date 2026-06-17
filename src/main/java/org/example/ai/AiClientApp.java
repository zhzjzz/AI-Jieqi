package org.example.ai;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.protocol.JsonProtocol;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClientApp extends Application {
    private static final double BOARD_WIDTH = 760;
    private static final double BOARD_HEIGHT = 840;
    private static final double MARGIN_X = 70;
    private static final double MARGIN_Y = 60;
    private static final double CELL_X = (BOARD_WIDTH - 2 * MARGIN_X) / 8.0;
    private static final double CELL_Y = (BOARD_HEIGHT - 2 * MARGIN_Y) / 9.0;
    private static final double PIECE_RADIUS = 24;
    private static final long MOVE_DELAY_MS = 650L;

    private final Canvas canvas = new Canvas(BOARD_WIDTH, BOARD_HEIGHT);
    private final Label statusLabel = new Label();
    private final Label turnLabel = new Label();
    private final Label sideLabel = new Label();
    private final ListView<String> logView = new ListView<>(FXCollections.observableArrayList());
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final AiDecisionLogger decisionLogger = new AiDecisionLogger(System.out);

    private WebSocketClient client;
    private AiAgent agent;
    private GameBoard board;
    private Piece.Side mySide;
    private Piece.Side currentTurn = Piece.Side.RED;
    private boolean gameOver;
    private boolean choosingMove;
    private int moveCount;
    private int lastMoveSourceRow = -1;
    private int lastMoveSourceCol = -1;
    private int lastMoveTargetRow = -1;
    private int lastMoveTargetCol = -1;

    public static void main(String[] args) {
        System.setProperty("prism.allowhidpi", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        String host = getParameters().getRaw().size() > 0 ? getParameters().getRaw().get(0) : "127.0.0.1";
        int port = parsePort(getParameters().getRaw().size() > 1 ? getParameters().getRaw().get(1) : "5000");
        agent = new AiAgent(AiConfig.load(), new HttpDeepSeekClient());

        Pane boardLayer = new Pane(canvas);
        boardLayer.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);

        statusLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        statusLabel.setTextFill(Color.web("#4a3421"));
        turnLabel.setFont(Font.font("Microsoft YaHei", 14));
        turnLabel.setTextFill(Color.web("#6b4f2a"));
        sideLabel.setFont(Font.font("Microsoft YaHei", 14));
        sideLabel.setTextFill(Color.web("#6b4f2a"));

        HBox infoBar = new HBox(24, statusLabel, sideLabel, turnLabel);
        infoBar.setAlignment(Pos.CENTER_LEFT);
        infoBar.setPadding(new Insets(0, 10, 8, 10));

        logView.setPrefWidth(300);
        logView.setStyle("-fx-font-size: 13px;");

        VBox rightPanel = new VBox(8, new Label("AI Battle Log"), logView);
        rightPanel.setPadding(new Insets(8));
        rightPanel.setPrefWidth(320);
        rightPanel.setStyle("-fx-background-color: rgba(255,255,255,0.42); -fx-background-radius: 12;");

        BorderPane center = new BorderPane();
        center.setLeft(boardLayer);
        center.setRight(rightPanel);
        BorderPane.setMargin(boardLayer, new Insets(0, 12, 0, 0));

        VBox root = new VBox(10, infoBar, center);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8edd8, #ead2a5);");

        stage.setTitle("AI Client - " + host + ":" + port);
        stage.setScene(new Scene(root));
        stage.setOnCloseRequest(event -> closeResources());
        stage.show();

        updateStatus("Connecting to ws://" + host + ":" + port);
        redraw();
        connect(host, port);
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 5000;
        }
    }

    private void connect(String host, int port) {
        try {
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Platform.runLater(() -> {
                        updateStatus("Connected. Waiting for match...");
                        log("Connected to ws://" + host + ":" + port);
                    });
                    send(JsonProtocol.message("startMatch").toString());
                }

                @Override
                public void onMessage(String message) {
                    handleServerMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Platform.runLater(() -> {
                        if (!gameOver) {
                            updateStatus("Connection closed: " + reason);
                            log("Connection closed");
                        }
                    });
                }

                @Override
                public void onError(Exception ex) {
                    Platform.runLater(() -> {
                        updateStatus("Connection failed");
                        log("Cannot connect to ws://" + host + ":" + port + ": " + ex.getMessage());
                    });
                }
            };
            client.connect();
        } catch (Exception e) {
            updateStatus("Connection failed");
            log(e.getMessage());
        }
    }

    private void handleServerMessage(String message) {
        JsonObject json;
        try {
            json = JsonProtocol.parse(message);
        } catch (Exception e) {
            Platform.runLater(() -> log("Invalid JSON from server: " + message));
            return;
        }

        String messageType = JsonProtocol.typeOf(json);
        switch (messageType) {
            case "matchSuccess" -> Platform.runLater(() -> {
                updateStatus("Match found. Starting...");
                log("Match success: " + JsonProtocol.string(json, "roomId", ""));
            });
            case "gameStart" -> Platform.runLater(() -> handleGameStart(json));
            case "moveResult" -> Platform.runLater(() -> handleMoveResult(json));
            case "gameOver" -> Platform.runLater(() -> handleGameOver(json));
            case "timeout" -> Platform.runLater(() -> handleTimeout(json));
            case "error" -> Platform.runLater(() -> log("Server error: " + JsonProtocol.string(json, "message", "")));
            default -> {
            }
        }
    }

    private void handleGameStart(JsonObject json) {
        mySide = JsonProtocol.sideFromColor(JsonProtocol.string(json, "yourColor", "black"));
        board = JsonProtocol.boardFromInitial(json.getAsJsonArray("initialBoard"));
        currentTurn = JsonProtocol.bool(json, "firstHand", false) ? mySide : opposite(mySide);
        gameOver = false;
        moveCount = 0;
        sideLabel.setText("Side: " + sideName(mySide));
        updateStatus("Game started");
        log("Game started as " + sideName(mySide));
        redraw();
        if (currentTurn == mySide) {
            chooseAndSendMove();
        }
    }

    private void handleMoveResult(JsonObject json) {
        boolean valid = JsonProtocol.bool(json, "valid", false);
        boolean success = JsonProtocol.bool(json, "success", false);
        if (!valid || !success) {
            log("Server rejected move");
            choosingMove = false;
            if (!gameOver && currentTurn == mySide) {
                chooseAndSendMove();
            }
            return;
        }

        Move move = JsonProtocol.toMove(json.getAsJsonObject("move"));
        if (json.has("flipResult")) {
            move.setType(JsonProtocol.pieceType(json.get("flipResult").getAsString()));
        }
        Piece.Side movedSide = sideAt(move.getSource());
        String record = applyMove(move);
        moveCount++;
        currentTurn = opposite(movedSide);
        log("#" + moveCount + " " + sideName(movedSide) + " " + record);
        choosingMove = false;
        updateStatus(currentTurn == mySide ? "AI thinking..." : "Watching opponent...");
        redraw();
        if (!gameOver && currentTurn == mySide) {
            chooseAndSendMove();
        }
    }

    private void handleGameOver(JsonObject json) {
        gameOver = true;
        choosingMove = false;
        Piece.Side winner = JsonProtocol.sideFromColor(JsonProtocol.string(json, "winner", "black"));
        String publicReason = JsonProtocol.string(json, "reason", "");
        String detailReason = JsonProtocol.string(json, "detailReason", publicReason);
        updateStatus(gameOverStatus(detailReason, winner == mySide));
        turnLabel.setText("Turn: game over");
        log("Game over. Winner: " + sideName(winner) + ", reason: " + detailReason);
        redraw();
    }

    private void handleTimeout(JsonObject json) {
        gameOver = true;
        choosingMove = false;
        updateStatus("Game over. Timeout");
        turnLabel.setText("Turn: game over");
        log("Timeout. Loser: " + JsonProtocol.string(json, "loserId", ""));
        redraw();
    }

    private String gameOverStatus(String reason, boolean win) {
        if ("disconnect".equals(reason)) {
            return win ? "\u5bf9\u5c40\u7ed3\u675f\uff1a\u5bf9\u65b9\u9000\u51fa\uff0c\u4f60\u8d62\u4e86" : "\u5bf9\u5c40\u7ed3\u675f\uff1a\u8fde\u63a5\u5df2\u65ad\u5f00";
        }
        if ("perpetual_check".equals(reason)) {
            return win ? "\u5bf9\u5c40\u7ed3\u675f\uff1a\u5bf9\u65b9\u957f\u5c06\u8fdd\u89c4\uff0c\u4f60\u8d62\u4e86" : "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u957f\u5c06\u8fdd\u89c4\uff0c\u5931\u8d25\u4e86";
        }
        if ("resign".equals(reason)) {
            return win ? "\u5bf9\u5c40\u7ed3\u675f\uff1a\u5bf9\u65b9\u8ba4\u8f93\uff0c\u4f60\u8d62\u4e86" : "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u5df2\u8ba4\u8f93";
        }
        return win ? "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u83b7\u80dc\u4e86" : "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u5931\u8d25\u4e86";
    }

    private void chooseAndSendMove() {
        if (choosingMove || gameOver || board == null || mySide == null || client == null || !client.isOpen()) {
            return;
        }
        choosingMove = true;
        updateStatus("AI thinking...");
        aiExecutor.submit(() -> {
            try {
                Thread.sleep(MOVE_DELAY_MS);
                AiDecision decision = agent.chooseMove(board, mySide, false);
                decision.move().setTurnStartTime(System.currentTimeMillis());
                decisionLogger.log(decision);
                Platform.runLater(() -> {
                    log("Send " + decision.move().getSource() + " -> " + decision.move().getDestination()
                            + " [" + decision.source() + "]");
                    if (client != null && client.isOpen()) {
                        client.send(JsonProtocol.moveMessage(decision.move()).toString());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    choosingMove = false;
                    log("AI interrupted");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    choosingMove = false;
                    log("AI failed: " + e.getMessage());
                    if (!gameOver && currentTurn == mySide) {
                        chooseAndSendMove();
                    }
                });
            }
        });
    }

    private String applyMove(Move move) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        rememberLastMove(sr, sc, dr, dc);
        if (source == null) {
            return "invalid " + move.getSource();
        }

        source.setRevealed(true);
        if (move.getType() != null) {
            source.setType(move.getType());
        }
        if (sr == dr && sc == dc) {
            return "reveal " + move.getSource() + " = " + pieceLabel(source);
        }

        Piece target = board.get(dr, dc);
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        if (target != null) {
            return move.getSource() + " x " + move.getDestination() + " (" + pieceLabel(target) + ")";
        }
        return move.getSource() + " -> " + move.getDestination();
    }

    private Piece.Side sideAt(String coord) {
        int row = board.rowFromCoord(coord);
        int col = board.colFromCoord(coord);
        Piece piece = board.get(row, col);
        return piece == null ? currentTurn : piece.getSide();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);
        drawHints(gc);
        drawPieces(gc);
        drawLabels(gc);
        if (gameOver) {
            turnLabel.setText("Turn: game over");
        } else if (mySide == null) {
            turnLabel.setText("Turn: waiting");
        } else {
            turnLabel.setText("Turn: " + sideName(currentTurn));
        }
    }

    private void drawBoard(GraphicsContext gc) {
        gc.setFill(Color.web("#f2d4a8"));
        gc.fillRect(0, 0, BOARD_WIDTH, BOARD_HEIGHT);

        gc.setFill(Color.web("#f8e8cc"));
        gc.fillRect(x(0) - 35, y(4) + 2, x(8) - x(0) + 70, y(5) - y(4) - 4);

        gc.setStroke(Color.web("#6b4f2a"));
        gc.setLineWidth(2);
        for (int r = 0; r < GameBoard.ROWS; r++) {
            gc.strokeLine(x(0), y(r), x(8), y(r));
        }
        for (int c = 0; c < GameBoard.COLS; c++) {
            gc.strokeLine(x(c), y(0), x(c), y(4));
            gc.strokeLine(x(c), y(5), x(c), y(9));
        }

        gc.strokeLine(x(3), y(0), x(5), y(2));
        gc.strokeLine(x(5), y(0), x(3), y(2));
        gc.strokeLine(x(3), y(7), x(5), y(9));
        gc.strokeLine(x(5), y(7), x(3), y(9));
    }

    private void drawHints(GraphicsContext gc) {
        gc.setFill(Color.web("#8a5e3b"));
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                gc.fillOval(x(boardToViewCol(c)) - 3, y(boardToViewRow(r)) - 3, 6, 6);
            }
        }
        drawLastMoveMarker(gc);
    }

    private void drawPieces(GraphicsContext gc) {
        if (board == null) {
            return;
        }
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                Piece piece = board.get(r, c);
                if (piece == null) {
                    continue;
                }
                double cx = x(boardToViewCol(c));
                double cy = y(boardToViewRow(r));
                gc.setFill(piece.getSide() == Piece.Side.RED ? Color.web("#b85a52") : Color.web("#5777b8"));
                gc.setStroke(Color.web("#553311"));
                gc.setLineWidth(2);
                gc.fillOval(cx - PIECE_RADIUS, cy - PIECE_RADIUS, PIECE_RADIUS * 2, PIECE_RADIUS * 2);
                gc.strokeOval(cx - PIECE_RADIUS, cy - PIECE_RADIUS, PIECE_RADIUS * 2, PIECE_RADIUS * 2);

                if (lastMoveTargetRow == r && lastMoveTargetCol == c) {
                    gc.setStroke(Color.web("#ffd84d"));
                    gc.setLineWidth(4);
                    gc.strokeOval(cx - PIECE_RADIUS - 9, cy - PIECE_RADIUS - 9, PIECE_RADIUS * 2 + 18, PIECE_RADIUS * 2 + 18);
                }

                gc.setFill(Color.web("#fff9ec"));
                gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
                gc.fillText(pieceLabel(piece), cx - 10, cy + 7);
            }
        }
    }

    private void drawLastMoveMarker(GraphicsContext gc) {
        if (lastMoveSourceRow < 0 || lastMoveSourceCol < 0) {
            return;
        }
        double sx = x(boardToViewCol(lastMoveSourceCol));
        double sy = y(boardToViewRow(lastMoveSourceRow));
        gc.setStroke(Color.web("#ffd84d"));
        gc.setLineWidth(3);
        gc.strokeRect(sx - PIECE_RADIUS - 7, sy - PIECE_RADIUS - 7, PIECE_RADIUS * 2 + 14, PIECE_RADIUS * 2 + 14);

        if (lastMoveTargetRow == lastMoveSourceRow && lastMoveTargetCol == lastMoveSourceCol) {
            return;
        }

        double tx = x(boardToViewCol(lastMoveTargetCol));
        double ty = y(boardToViewRow(lastMoveTargetRow));
        gc.setStroke(Color.web("#ff9f1a"));
        gc.setLineWidth(3);
        gc.strokeLine(sx, sy, tx, ty);
    }

    private void drawLabels(GraphicsContext gc) {
        gc.setFill(Color.web("#6b4f2a"));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        gc.fillText("\u695a\u6cb3", x(2) - 45, (y(4) + y(5)) / 2 + 8);
        gc.fillText("\u6c49\u754c", x(5) + 10, (y(4) + y(5)) / 2 + 8);
    }

    private double x(int col) {
        return MARGIN_X + col * CELL_X;
    }

    private double y(int row) {
        return MARGIN_Y + row * CELL_Y;
    }

    private int boardToViewRow(int row) {
        return mySide == Piece.Side.BLACK ? GameBoard.ROWS - 1 - row : row;
    }

    private int boardToViewCol(int col) {
        return mySide == Piece.Side.BLACK ? GameBoard.COLS - 1 - col : col;
    }

    private void rememberLastMove(int sr, int sc, int dr, int dc) {
        lastMoveSourceRow = sr;
        lastMoveSourceCol = sc;
        lastMoveTargetRow = dr;
        lastMoveTargetCol = dc;
    }

    private String pieceLabel(Piece piece) {
        return piece.shortName();
    }

    private String sideName(Piece.Side side) {
        return side == Piece.Side.RED ? "RED" : "BLACK";
    }

    private Piece.Side opposite(Piece.Side side) {
        return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
    }

    private void log(String text) {
        logView.getItems().add(0, text);
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private void closeResources() {
        gameOver = true;
        aiExecutor.shutdownNow();
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
    }
}
