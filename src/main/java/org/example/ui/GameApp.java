package org.example.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameApp extends Application {
    private static Socket socketRef;
    private static ObjectInputStream inputRef;
    private static ObjectOutputStream outputRef;
    private static String colorRef;
    private static long seedRef;

    public static void configure(Socket socket, ObjectInputStream input, ObjectOutputStream output, String color, long seed) {
        socketRef = socket;
        inputRef = input;
        outputRef = output;
        colorRef = color;
        seedRef = seed;
    }

    private static final double BOARD_WIDTH = 760;
    private static final double BOARD_HEIGHT = 840;
    private static final double MARGIN_X = 70;
    private static final double MARGIN_Y = 60;
    private static final double CELL_X = (BOARD_WIDTH - 2 * MARGIN_X) / 8.0;
    private static final double CELL_Y = (BOARD_HEIGHT - 2 * MARGIN_Y) / 9.0;
    private static final double PIECE_RADIUS = 24;

    private final Canvas canvas = new Canvas(BOARD_WIDTH, BOARD_HEIGHT);
    private GameBoard board;
    private Piece.Side mySide;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean myTurn = false;

    @Override
    public void start(Stage stage) {
        board = new GameBoard(seedRef);
        mySide = "RED".equals(colorRef) ? Piece.Side.RED : Piece.Side.BLACK;
        myTurn = mySide == Piece.Side.RED;

        Pane boardLayer = new Pane();
        boardLayer.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);
        boardLayer.getChildren().add(canvas);

        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                var point = new javafx.scene.control.Button();
                point.setPrefSize(36, 36);
                point.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                final int row = r;
                final int col = c;
                point.setOnAction(e -> handleClick(row, col));
                point.setLayoutX(x(col) - 18);
                point.setLayoutY(y(r) - 18);
                boardLayer.getChildren().add(point);
            }
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_CENTER);
        root.getChildren().add(boardLayer);

        stage.setTitle("揭棋对弈 - " + colorRef);
        stage.setScene(new Scene(root));
        stage.setOnCloseRequest(event -> closeResources());
        stage.show();

        redraw();
        startReceiver();
    }

    private void handleClick(int row, int col) {
        if (!myTurn) {
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
                redraw();
                return;
            }
            if (!piece.isRevealed()) {
                sendMove(new Move(board.coord(row, col), board.coord(row, col), null, System.currentTimeMillis()));
                myTurn = false;
            }
            return;
        }

        Move move = new Move(board.coord(selectedRow, selectedCol), board.coord(row, col), null, System.currentTimeMillis());
        sendMove(move);
        myTurn = false;
        selectedRow = -1;
        selectedCol = -1;
    }

    private void sendMove(Move move) {
        try {
            outputRef.writeObject(move);
            outputRef.flush();
        } catch (Exception e) {
            showError("发送失败: " + e.getMessage());
        }
    }

    private void startReceiver() {
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    Object obj = inputRef.readObject();
                    if (obj instanceof Move move) {
                        Platform.runLater(() -> {
                            applyMove(move);
                            myTurn = true;
                            redraw();
                        });
                    } else {
                        String text = String.valueOf(obj);
                        Platform.runLater(() -> {
                            if (text.startsWith("ERROR:")) {
                                myTurn = true;
                                showError(text.substring(6));
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("连接已断开: " + e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
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
            return;
        }
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        source.setRevealed(true);
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);
        drawPieces(gc);
        drawLabels(gc);
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

                if (selectedRow == r && selectedCol == c) {
                    gc.setStroke(Color.web("#ff7a00"));
                    gc.setLineWidth(3);
                    gc.strokeOval(cx - PIECE_RADIUS - 4, cy - PIECE_RADIUS - 4, PIECE_RADIUS * 2 + 8, PIECE_RADIUS * 2 + 8);
                }

                gc.setFill(Color.web("#2b1d12"));
                gc.setFont(Font.font("Serif", FontWeight.BOLD, 20));
                gc.fillText(piece.shortName(), cx - 10, cy + 7);
            }
        }
    }

    private void drawLabels(GraphicsContext gc) {
        gc.setFill(Color.web("#6b4f2a"));
        gc.setFont(Font.font("Serif", FontWeight.BOLD, 18));
        gc.fillText("楚河", x(2) - 45, (y(4) + y(5)) / 2 + 8);
        gc.fillText("汉界", x(5) + 10, (y(4) + y(5)) / 2 + 8);
    }

    private double x(int col) {
        return MARGIN_X + col * CELL_X;
    }

    private double y(int row) {
        return MARGIN_Y + row * CELL_Y;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void closeResources() {
        try {
            if (socketRef != null) {
                socketRef.close();
            }
        } catch (Exception ignored) {
        }
    }
}
