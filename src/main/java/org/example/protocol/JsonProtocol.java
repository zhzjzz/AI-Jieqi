package org.example.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;

public final class JsonProtocol {
    private JsonProtocol() {
    }

    public static JsonObject parse(String message) {
        return JsonParser.parseString(message).getAsJsonObject();
    }

    public static String typeOf(JsonObject json) {
        return string(json, "messageType", "");
    }

    public static JsonObject message(String messageType) {
        JsonObject json = new JsonObject();
        json.addProperty("messageType", messageType);
        return json;
    }

    public static Move toMove(JsonObject json) {
        String source = string(json, "fromX", "a") + integer(json, "fromY", 0);
        String destination = string(json, "toX", "a") + integer(json, "toY", 0);
        return new Move(source, destination, null, System.currentTimeMillis());
    }

    public static JsonObject moveMessage(Move move) {
        JsonObject json = moveObject(move);
        json.addProperty("messageType", "move");
        return json;
    }

    public static JsonObject moveResult(Move move, boolean success, boolean valid) {
        JsonObject json = message("moveResult");
        json.addProperty("success", success);
        json.add("move", moveObject(move));
        json.addProperty("valid", valid);
        if (isFlip(move) && move.getType() != null) {
            json.addProperty("flipResult", pieceName(move.getType()));
        }
        return json;
    }

    public static JsonObject moveObject(Move move) {
        JsonObject json = new JsonObject();
        json.addProperty("fromX", x(move.getSource()));
        json.addProperty("fromY", y(move.getSource()));
        json.addProperty("toX", x(move.getDestination()));
        json.addProperty("toY", y(move.getDestination()));
        json.addProperty("isFlip", isFlip(move));
        return json;
    }

    public static JsonArray initialBoard(GameBoard board) {
        JsonArray cells = new JsonArray();
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                if (piece == null) {
                    continue;
                }
                JsonObject cell = new JsonObject();
                cell.addProperty("x", String.valueOf((char) ('a' + col)));
                cell.addProperty("y", row);
                cell.addProperty("piece", pieceName(piece.getType()));
                cell.addProperty("color", colorOf(piece.getSide()));
                cell.addProperty("visible", piece.isRevealed());
                cells.add(cell);
            }
        }
        return cells;
    }

    public static GameBoard boardFromInitial(JsonArray initialBoard) {
        GameBoard board = new GameBoard(0L);
        board.clear();
        for (JsonElement element : initialBoard) {
            JsonObject cell = element.getAsJsonObject();
            int row = integer(cell, "y", 0);
            int col = string(cell, "x", "a").charAt(0) - 'a';
            Piece.Side side = sideFromColor(string(cell, "color", row <= 4 ? "black" : "red"));
            int type = pieceType(string(cell, "piece", "pawn"));
            boolean visible = bool(cell, "visible", false);
            board.set(row, col, new Piece(side, type, visible));
        }
        return board;
    }

    public static JsonObject loginResult(boolean success, String message, String userId) {
        JsonObject json = message("loginResult");
        json.addProperty("success", success);
        json.addProperty("message", message);
        if (userId != null) {
            json.addProperty("userId", userId);
        }
        return json;
    }

    public static JsonObject matchSuccess(String roomId, String opponentId, String opponentNickname) {
        JsonObject json = message("matchSuccess");
        json.addProperty("roomId", roomId);
        json.addProperty("opponentId", opponentId);
        json.addProperty("opponentNickname", opponentNickname);
        return json;
    }

    public static JsonObject gameStart(String redPlayerId, String blackPlayerId, Piece.Side yourSide, boolean firstHand,
                                       GameBoard board) {
        JsonObject json = message("gameStart");
        json.addProperty("redPlayerId", redPlayerId);
        json.addProperty("blackPlayerId", blackPlayerId);
        json.addProperty("yourColor", colorOf(yourSide));
        json.addProperty("firstHand", firstHand);
        json.add("initialBoard", initialBoard(board));
        return json;
    }

    public static JsonObject roomInfo(boolean opponentReady) {
        JsonObject json = message("roomInfo");
        json.addProperty("opponentReady", opponentReady);
        return json;
    }

    public static JsonObject pong(long timestamp) {
        JsonObject json = message("pong");
        json.addProperty("timestamp", timestamp);
        return json;
    }

    public static JsonObject error(int code, String message) {
        JsonObject json = message("error");
        json.addProperty("code", code);
        json.addProperty("message", message);
        return json;
    }

    public static JsonObject gameOver(Piece.Side winner, String reason, String winnerId) {
        JsonObject json = message("gameOver");
        json.addProperty("winner", colorOf(winner));
        json.addProperty("reason", reason);
        json.addProperty("winnerId", winnerId);
        return json;
    }

    public static JsonObject timeout(String loserId, String winnerId) {
        JsonObject json = message("timeout");
        json.addProperty("loserId", loserId);
        json.addProperty("winnerId", winnerId);
        json.addProperty("reason", "timeout");
        return json;
    }

    public static Piece.Side sideFromColor(String color) {
        return "red".equalsIgnoreCase(color) ? Piece.Side.RED : Piece.Side.BLACK;
    }

    public static String colorOf(Piece.Side side) {
        return side == Piece.Side.RED ? "red" : "black";
    }

    public static String pieceName(int type) {
        return switch (type) {
            case 0 -> "king";
            case 1 -> "rook";
            case 2 -> "knight";
            case 3 -> "cannon";
            case 4 -> "pawn";
            case 5 -> "guard";
            case 6 -> "bishop";
            default -> "unknown";
        };
    }

    public static int pieceType(String pieceName) {
        return switch (pieceName.toLowerCase()) {
            case "king" -> 0;
            case "rook" -> 1;
            case "knight" -> 2;
            case "cannon" -> 3;
            case "pawn" -> 4;
            case "guard" -> 5;
            case "bishop" -> 6;
            default -> -1;
        };
    }

    public static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    public static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
    }

    public static long longValue(JsonObject json, String key, long fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : fallback;
    }

    public static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static boolean isFlip(Move move) {
        return move.getSource() != null && move.getSource().equals(move.getDestination());
    }

    private static String x(String coord) {
        return coord == null || coord.isBlank() ? "a" : String.valueOf(coord.charAt(0));
    }

    private static int y(String coord) {
        return coord == null || coord.length() < 2 ? 0 : Character.getNumericValue(coord.charAt(1));
    }
}
