package org.example.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
import com.google.gson.JsonObject;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;
import org.example.protocol.JsonProtocol;
import org.java_websocket.client.WebSocketClient;

import java.util.ArrayList;
import java.util.List;

public class GameApp extends Application {
    private static WebSocketClient clientRef;
    private static String colorRef;
    private static GameBoard initialBoardRef;
    private static boolean initialTurnRef;
    private static GameApp runningApp;

    private static final double BOARD_WIDTH = 760;
    private static final double BOARD_HEIGHT = 840;
    private static final double MARGIN_X = 70;
    private static final double MARGIN_Y = 60;
    private static final double CELL_X = (BOARD_WIDTH - 2 * MARGIN_X) / 8.0;
    private static final double CELL_Y = (BOARD_HEIGHT - 2 * MARGIN_Y) / 9.0;
    private static final double PIECE_RADIUS = 24;

    public static void configure(WebSocketClient client, String color, GameBoard board, boolean initialTurn) {
        clientRef = client;
        colorRef = color;
        initialBoardRef = board;
        initialTurnRef = initialTurn;
    }

    public static boolean isRunning() {
        return runningApp != null;
    }

    public static void receive(JsonObject json) {
        Platform.runLater(() -> {
            if (runningApp != null) {
                runningApp.handleServerMessage(json);
            }
        });
    }

    private final Canvas canvas = new Canvas(BOARD_WIDTH, BOARD_HEIGHT);
    private final Label statusLabel = new Label();
    private final Label turnLabel = new Label();
    private final ListView<String> logView = new ListView<>(FXCollections.observableArrayList());
    private final List<int[]> legalTargets = new ArrayList<>();

    private GameBoard board;
    private RuleEngine ruleEngine;
    private Piece.Side mySide;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int lastMoveSourceRow = -1;
    private int lastMoveSourceCol = -1;
    private int lastMoveTargetRow = -1;
    private int lastMoveTargetCol = -1;
    private boolean myTurn;
    private boolean gameOver;
    private boolean inCheck;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        runningApp = this;
        primaryStage = stage;
        board = initialBoardRef;
        ruleEngine = new RuleEngine();
        mySide = JsonProtocol.sideFromColor(colorRef);
        myTurn = initialTurnRef;

        Pane boardLayer = new Pane();
        boardLayer.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);
        boardLayer.getChildren().add(canvas);

        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                var point = new javafx.scene.control.Button();
                point.setPrefSize(36, 36);
                point.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                final int viewRow = boardToViewRow(r);
                final int viewCol = boardToViewCol(c);
                point.setOnAction(e -> handleClick(viewRow, viewCol));
                point.setLayoutX(x(viewCol) - 18);
                point.setLayoutY(y(viewRow) - 18);
                boardLayer.getChildren().add(point);
            }
        }

        statusLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        statusLabel.setTextFill(Color.web("#4a3421"));
        turnLabel.setFont(Font.font("Microsoft YaHei", 14));
        turnLabel.setTextFill(Color.web("#6b4f2a"));

        HBox infoBar = new HBox(24, statusLabel, turnLabel);
        infoBar.setAlignment(Pos.CENTER_LEFT);
        infoBar.setPadding(new Insets(0, 10, 8, 10));

        logView.setPrefWidth(260);
        logView.setStyle("-fx-font-size: 13px;");

        VBox rightPanel = new VBox(8, new Label("对局记录"), logView);
        rightPanel.setPadding(new Insets(8));
        rightPanel.setPrefWidth(280);
        rightPanel.setStyle("-fx-background-color: rgba(255,255,255,0.42); -fx-background-radius: 12;");

        BorderPane center = new BorderPane();
        center.setLeft(boardLayer);
        center.setRight(rightPanel);
        BorderPane.setMargin(boardLayer, new Insets(0, 12, 0, 0));

        VBox root = new VBox(10, infoBar, center);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8edd8, #ead2a5);");

        stage.setTitle("揭棋对局 - " + colorRef);
        stage.setScene(new Scene(root));
        stage.setOnCloseRequest(event -> closeResources());
        stage.show();

        updateStatus("已连接，执棋方：" + ("red".equalsIgnoreCase(colorRef) ? "红方" : "黑方"));
        redraw();
    }

    private void handleClick(int viewRow, int viewCol) {
        if (!myTurn || gameOver) {
            return;
        }
        int row = viewToBoardRow(viewRow);
        int col = viewToBoardCol(viewCol);

        if (legalTargets.stream().anyMatch(p -> p[0] == row && p[1] == col)) {
            sendMove(new Move(board.coord(selectedRow, selectedCol), board.coord(row, col), null, System.currentTimeMillis()));
            log("走子: " + board.coord(selectedRow, selectedCol) + " -> " + board.coord(row, col));
            clearSelection();
            myTurn = false;
            updateStatus("走子请求已发送");
            redraw();
            return;
        }

        Piece piece = board.get(row, col);
        if (selectedRow == -1) {
            if (piece == null) {
                return;
            }
            if (piece.getSide() == mySide) {
                selectedRow = row;
                selectedCol = col;
                refreshLegalTargets();
                updateStatus("已选中 " + piece.shortName() + "，请选择目标位置");
                redraw();
                return;
            }
            if (!piece.isRevealed()) {
                sendMove(new Move(board.coord(row, col), board.coord(row, col), null, System.currentTimeMillis()));
                log("翻子: " + board.coord(row, col));
                myTurn = false;
                updateStatus("翻子请求已发送");
            }
            return;
        }

        if (selectedRow == row && selectedCol == col) {
            clearSelection();
            updateStatus("已取消选择");
            redraw();
            return;
        }

        if (piece != null && piece.getSide() == mySide) {
            selectedRow = row;
            selectedCol = col;
            refreshLegalTargets();
            updateStatus("已切换到 " + piece.shortName());
            redraw();
        }
    }

    private void refreshLegalTargets() {
        legalTargets.clear();
        if (selectedRow < 0) {
            return;
        }
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                if (r == selectedRow && c == selectedCol) {
                    continue;
                }
                Move move = new Move(board.coord(selectedRow, selectedCol), board.coord(r, c), null, System.currentTimeMillis());
                if (ruleEngine.isLegalMove(board, move, mySide)) {
                    legalTargets.add(new int[]{r, c});
                }
            }
        }
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        legalTargets.clear();
    }

    private void sendMove(Move move) {
        try {
            clientRef.send(JsonProtocol.moveMessage(move).toString());
        } catch (Exception e) {
            showError("发送失败: " + e.getMessage());
        }
    }

    private void handleServerMessage(JsonObject message) {
        String messageType = JsonProtocol.typeOf(message);
        if ("error".equals(messageType)) {
            myTurn = true;
            updateStatus("操作无效: " + JsonProtocol.string(message, "message", ""));
            redraw();
            return;
        }
        if ("moveResult".equals(messageType)) {
            handleMoveResult(message);
            return;
        }
        if ("timeout".equals(messageType)) {
            handleTimeout(message);
            return;
        }
        if ("gameOver".equals(messageType)) {
            gameOver = true;
            myTurn = false;
            Piece.Side winner = JsonProtocol.sideFromColor(JsonProtocol.string(message, "winner", "black"));
            boolean iWin = winner == mySide;
            String publicReason = JsonProtocol.string(message, "reason", "");
            String detailReason = JsonProtocol.string(message, "detailReason", publicReason);
            updateStatus(gameOverStatus(detailReason, iWin));
            log("对局结束，胜者：" + winner + "，原因：" + detailReason);
            redraw();
            showGameOverDialog(iWin, detailReason);
        }
    }

    private void handleMoveResult(JsonObject message) {
        boolean valid = JsonProtocol.bool(message, "valid", false);
        boolean success = JsonProtocol.bool(message, "success", false);
        if (!valid || !success) {
            myTurn = true;
            updateStatus("走子无效，请重新操作");
            redraw();
            return;
        }
        Move move = JsonProtocol.toMove(message.getAsJsonObject("move"));
        if (message.has("flipResult")) {
            move.setType(JsonProtocol.pieceType(message.get("flipResult").getAsString()));
        }
        Piece.Side movedSide = sideAt(move.getSource());
        String record = applyMove(move);
        if (record != null) {
            log(record);
        }
        Piece.Side nextSide = movedSide == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
        myTurn = nextSide == mySide;
        updateStatus(myTurn ? "轮到你操作" : "等待对方操作");
        redraw();
    }

    private void handleTimeout(JsonObject message) {
        gameOver = true;
        myTurn = false;
        String loserId = JsonProtocol.string(message, "loserId", "");
        updateStatus("对局结束：玩家 " + loserId + " 超时");
        redraw();
        showGameOverDialog(false, "timeout");
    }

    private String applyMove(Move move) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        if (source == null) {
            return null;
        }
        rememberLastMove(sr, sc, dr, dc);
        if (sr == dr && sc == dc) {
            source.setRevealed(true);
            if (move.getType() != null) {
                source.setType(move.getType());
            }
            return "翻子: " + move.getSource() + " -> " + source.shortName();
        }
        Piece target = board.get(dr, dc);
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        source.setRevealed(true);
        if (move.getType() != null) {
            source.setType(move.getType());
        }
        if (target != null) {
            return "吃子: " + move.getSource() + " x " + move.getDestination() + " (" + target.shortName() + ")";
        }
        return "走子: " + move.getSource() + " -> " + move.getDestination();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);
        drawHints(gc);
        drawPieces(gc);
        drawLabels(gc);
        turnLabel.setText(myTurn ? "当前状态：你的回合" : "当前状态：对方回合");
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
        gc.setFill(Color.web("#ff7a00"));
        for (int[] p : legalTargets) {
            gc.fillOval(x(boardToViewCol(p[1])) - 7, y(boardToViewRow(p[0])) - 7, 14, 14);
        }
    }

    private void drawPieces(GraphicsContext gc) {
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

                if (selectedRow == r && selectedCol == c) {
                    gc.setStroke(Color.web("#ff7a00"));
                    gc.setLineWidth(4);
                    gc.strokeOval(cx - PIECE_RADIUS - 5, cy - PIECE_RADIUS - 5, PIECE_RADIUS * 2 + 10, PIECE_RADIUS * 2 + 10);
                }
                if (lastMoveTargetRow == r && lastMoveTargetCol == c) {
                    gc.setStroke(Color.web("#ffd84d"));
                    gc.setLineWidth(4);
                    gc.strokeOval(cx - PIECE_RADIUS - 9, cy - PIECE_RADIUS - 9, PIECE_RADIUS * 2 + 18, PIECE_RADIUS * 2 + 18);
                }

                gc.setFill(Color.web("#fff9ec"));
                gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
                gc.fillText(piece.shortName(), cx - 10, cy + 7);
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
        gc.fillText(mySide == Piece.Side.RED ? "楚河" : "汉界", x(2) - 45, (y(4) + y(5)) / 2 + 8);
        gc.fillText(mySide == Piece.Side.RED ? "汉界" : "楚河", x(5) + 10, (y(4) + y(5)) / 2 + 8);
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

    private int viewToBoardRow(int row) {
        return boardToViewRow(row);
    }

    private int viewToBoardCol(int col) {
        return boardToViewCol(col);
    }

    private void log(String text) {
        logView.getItems().add(0, text);
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private Piece.Side sideAt(String coord) {
        int row = board.rowFromCoord(coord);
        int col = board.colFromCoord(coord);
        Piece piece = board.get(row, col);
        return piece == null ? mySide : piece.getSide();
    }

    private void rememberLastMove(int sr, int sc, int dr, int dc) {
        lastMoveSourceRow = sr;
        lastMoveSourceCol = sc;
        lastMoveTargetRow = dr;
        lastMoveTargetCol = dc;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String gameOverStatus(String reason, boolean win) {
        if ("disconnect".equals(reason)) {
            return win ? "对局结束：对方退出，你赢了" : "对局结束：连接已断开";
        }
        if ("perpetual_check".equals(reason)) {
            return win ? "对局结束：对方长将违规，你赢了" : "对局结束：你长将违规，失败了";
        }
        if ("resign".equals(reason)) {
            return win ? "对局结束：对方认输，你赢了" : "对局结束：你已认输";
        }
        if ("timeout".equals(reason)) {
            return win ? "对局结束：对方超时，你赢了" : "对局结束：你超时了";
        }
        return win ? "对局结束：你获胜了" : "对局结束：你失败了";
    }

    private void showGameOverDialog(boolean win, String reason) {
        ButtonType closeButton = new ButtonType("关闭", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, gameOverStatus(reason, win), closeButton);
        alert.setHeaderText(null);
        alert.setTitle("对局结束");
        alert.showAndWait();
        closeResources();
        if (primaryStage != null) {
            primaryStage.close();
        }
        Platform.exit();
    }

    private void closeResources() {
        try {
            runningApp = null;
            if (clientRef != null) {
                clientRef.close();
            }
        } catch (Exception ignored) {
        }
    }
}
