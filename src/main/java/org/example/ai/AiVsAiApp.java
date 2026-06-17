package org.example.ai;

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
import org.example.common.RuleEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiVsAiApp extends Application {
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
    private final ListView<String> logView = new ListView<>(FXCollections.observableArrayList());
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();

    private GameBoard board;
    private RuleEngine ruleEngine;
    private AiAgent redAgent;
    private AiAgent blackAgent;
    private Piece.Side turn = Piece.Side.RED;
    private boolean gameOver;
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
        board = new GameBoard(System.currentTimeMillis());
        ruleEngine = new RuleEngine();
        AiConfig config = AiConfig.load();
        redAgent = new AiAgent(config, new HttpDeepSeekClient());
        blackAgent = new AiAgent(config, new HttpDeepSeekClient());

        Pane boardLayer = new Pane(canvas);
        boardLayer.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);

        statusLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        statusLabel.setTextFill(Color.web("#4a3421"));
        turnLabel.setFont(Font.font("Microsoft YaHei", 14));
        turnLabel.setTextFill(Color.web("#6b4f2a"));

        HBox infoBar = new HBox(24, statusLabel, turnLabel);
        infoBar.setAlignment(Pos.CENTER_LEFT);
        infoBar.setPadding(new Insets(0, 10, 8, 10));

        logView.setPrefWidth(300);
        logView.setStyle("-fx-font-size: 13px;");

        VBox rightPanel = new VBox(8, new Label("AI 对 AI 记录"), logView);
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

        stage.setTitle("揭棋 AI 对 AI 自动演示");
        stage.setScene(new Scene(root));
        stage.setOnCloseRequest(event -> {
            gameOver = true;
            aiExecutor.shutdownNow();
        });
        stage.show();

        updateStatus("AI 对 AI 自动演示已开始");
        redraw();
        scheduleNextMove();
    }

    private void scheduleNextMove() {
        if (gameOver) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(MOVE_DELAY_MS);
                aiExecutor.submit(this::chooseMoveInBackground);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void chooseMoveInBackground() {
        if (gameOver) {
            return;
        }
        try {
            AiAgent agent = turn == Piece.Side.RED ? redAgent : blackAgent;
            AiDecision decision = agent.chooseMove(board, turn, ruleEngine.isInCheck(board, turn));
            Platform.runLater(() -> applyAiDecision(decision));
        } catch (Exception e) {
            Platform.runLater(() -> finishGame(opposite(turn), "无合法走子或 AI 决策失败: " + e.getMessage()));
        }
    }

    private void applyAiDecision(AiDecision decision) {
        if (gameOver) {
            return;
        }
        Move move = decision.move();
        if (!ruleEngine.isLegalMove(board, move, turn)) {
            finishGame(opposite(turn), "AI 产生非法走子");
            return;
        }

        String record = applyMove(move);
        moveCount++;
        log((turn == Piece.Side.RED ? "红方" : "黑方") + " " + record + " [" + decision.source() + "]");

        Piece.Side winner = winnerAfterMove();
        if (winner != null) {
            redraw();
            finishGame(winner, "对局结束");
            return;
        }

        turn = opposite(turn);
        updateStatus("第 " + moveCount + " 手完成");
        redraw();
        scheduleNextMove();
    }

    private String applyMove(Move move) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        rememberLastMove(sr, sc, dr, dc);
        if (source == null) {
            return "无效位置 " + move.getSource();
        }

        source.setRevealed(true);
        move.setType(source.getType());
        if (sr == dr && sc == dc) {
            return "翻子: " + move.getSource() + " -> " + source.shortName();
        }

        Piece target = board.get(dr, dc);
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        if (target != null) {
            return "吃子: " + move.getSource() + " x " + move.getDestination() + " (" + target.shortName() + ")";
        }
        return "走子: " + move.getSource() + " -> " + move.getDestination();
    }

    private Piece.Side winnerAfterMove() {
        if (ruleEngine.isGeneralCaptured(board, Piece.Side.RED)) {
            return Piece.Side.BLACK;
        }
        if (ruleEngine.isGeneralCaptured(board, Piece.Side.BLACK)) {
            return Piece.Side.RED;
        }
        Piece.Side nextTurn = opposite(turn);
        if (!ruleEngine.hasAnyLegalMove(board, nextTurn)) {
            return turn;
        }
        return null;
    }

    private void finishGame(Piece.Side winner, String reason) {
        gameOver = true;
        updateStatus(reason + "，胜者：" + (winner == Piece.Side.RED ? "红方" : "黑方"));
        turnLabel.setText("当前状态：对局结束");
        log("对局结束，胜者：" + (winner == Piece.Side.RED ? "红方" : "黑方"));
        aiExecutor.shutdownNow();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);
        drawHints(gc);
        drawPieces(gc);
        drawLabels(gc);
        turnLabel.setText(gameOver ? "当前状态：对局结束" : "当前状态：" + (turn == Piece.Side.RED ? "红方思考" : "黑方思考"));
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
                gc.fillOval(x(c) - 3, y(r) - 3, 6, 6);
            }
        }
        drawLastMoveMarker(gc);
    }

    private void drawPieces(GraphicsContext gc) {
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                Piece piece = board.get(r, c);
                if (piece == null) {
                    continue;
                }
                double cx = x(c);
                double cy = y(r);
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
                gc.fillText(piece.shortName(), cx - 10, cy + 7);
            }
        }
    }

    private void drawLastMoveMarker(GraphicsContext gc) {
        if (lastMoveSourceRow < 0 || lastMoveSourceCol < 0) {
            return;
        }
        double sx = x(lastMoveSourceCol);
        double sy = y(lastMoveSourceRow);
        gc.setStroke(Color.web("#ffd84d"));
        gc.setLineWidth(3);
        gc.strokeRect(sx - PIECE_RADIUS - 7, sy - PIECE_RADIUS - 7, PIECE_RADIUS * 2 + 14, PIECE_RADIUS * 2 + 14);

        if (lastMoveTargetRow == lastMoveSourceRow && lastMoveTargetCol == lastMoveSourceCol) {
            return;
        }

        double tx = x(lastMoveTargetCol);
        double ty = y(lastMoveTargetRow);
        gc.setStroke(Color.web("#ff9f1a"));
        gc.setLineWidth(3);
        gc.strokeLine(sx, sy, tx, ty);
    }

    private void drawLabels(GraphicsContext gc) {
        gc.setFill(Color.web("#6b4f2a"));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        gc.fillText("楚河", x(2) - 45, (y(4) + y(5)) / 2 + 8);
        gc.fillText("汉界", x(5) + 10, (y(4) + y(5)) / 2 + 8);
    }

    private double x(int col) {
        return MARGIN_X + col * CELL_X;
    }

    private double y(int row) {
        return MARGIN_Y + row * CELL_Y;
    }

    private void log(String text) {
        logView.getItems().add(0, text);
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private Piece.Side opposite(Piece.Side side) {
        return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
    }

    private void rememberLastMove(int sr, int sc, int dr, int dc) {
        lastMoveSourceRow = sr;
        lastMoveSourceCol = sc;
        lastMoveTargetRow = dr;
        lastMoveTargetCol = dc;
    }
}
