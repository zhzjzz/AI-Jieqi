package org.example.net;

import com.google.gson.JsonObject;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;
import org.example.protocol.JsonProtocol;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameServer extends WebSocketServer {
    private static final int DEFAULT_PORT = 5000;
    private static final long TURN_TIMEOUT_SECONDS = 60;
    private static final int REPEATED_CHECK_LIMIT = 3;

    private final Map<WebSocket, PlayerConnection> players = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();
    private final Queue<PlayerConnection> waitingPlayers = new ArrayDeque<>();
    private final RuleEngine ruleEngine = new RuleEngine();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public GameServer(int port) {
        super(new InetSocketAddress(port));
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GameServer server = new GameServer(port);
        server.start();
        System.out.println("WebSocket server running at ws://localhost:" + port);
    }

    @Override
    public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {
        PlayerConnection player = new PlayerConnection(conn, "guest-" + shortId(), "游客" + players.size());
        players.put(conn, player);
        System.out.println("New connection: " + conn.getRemoteSocketAddress());
    }

    @Override
    public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {
        PlayerConnection player = players.remove(conn);
        if (player == null) {
            return;
        }
        waitingPlayers.remove(player);
        if (player.room != null) {
            player.room.finishByDisconnect(player);
        }
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public synchronized void onMessage(WebSocket conn, String message) {
        PlayerConnection player = players.get(conn);
        if (player == null) {
            return;
        }
        JsonObject json;
        try {
            json = JsonProtocol.parse(message);
        } catch (Exception e) {
            send(player, JsonProtocol.error(4001, "JSON format error"));
            return;
        }

        try {
            handleMessage(player, json);
        } catch (Exception e) {
            send(player, JsonProtocol.error(4001, e.getMessage() == null ? "bad request" : e.getMessage()));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port " + getPort());
    }

    private void handleMessage(PlayerConnection player, JsonObject json) {
        String messageType = JsonProtocol.typeOf(json);
        switch (messageType) {
            case "Login" -> handleLogin(player, json);
            case "register" -> handleRegister(player, json);
            case "startMatch" -> startMatch(player);
            case "cancelMatch" -> cancelMatch(player);
            case "requestFirstHand" -> player.wannaFirst = JsonProtocol.bool(json, "wannaFirst", false);
            case "Ready" -> handleReady(player);
            case "ping" -> send(player, JsonProtocol.pong(JsonProtocol.longValue(json, "timestamp", System.currentTimeMillis())));
            case "Resign" -> handleResign(player);
            case "move" -> handleMove(player, json);
            default -> send(player, JsonProtocol.error(4001, "unsupported messageType: " + messageType));
        }
    }

    private void handleLogin(PlayerConnection player, JsonObject json) {
        String userId = JsonProtocol.string(json, "userId", "");
        String password = JsonProtocol.string(json, "password", "");
        Account account = accounts.get(userId);
        if (account == null || !Objects.equals(account.password, password)) {
            send(player, JsonProtocol.loginResult(false, "账号或密码错误", null));
            return;
        }
        boolean duplicate = players.values().stream()
                .anyMatch(other -> other != player && userId.equals(other.userId) && other.loggedIn);
        if (duplicate) {
            send(player, JsonProtocol.error(1002, "重复登录"));
            return;
        }
        player.userId = userId;
        player.nickname = account.nickname;
        player.loggedIn = true;
        send(player, JsonProtocol.loginResult(true, "登录成功", userId));
    }

    private void handleRegister(PlayerConnection player, JsonObject json) {
        String userId = JsonProtocol.string(json, "userId", "");
        String password = JsonProtocol.string(json, "password", "");
        String nickname = JsonProtocol.string(json, "nickname", userId);
        if (userId.isBlank() || password.isBlank()) {
            send(player, JsonProtocol.loginResult(false, "账号和密码不能为空", null));
            return;
        }
        if (accounts.containsKey(userId)) {
            send(player, JsonProtocol.loginResult(false, "账号已存在", null));
            return;
        }
        accounts.put(userId, new Account(password, nickname));
        player.userId = userId;
        player.nickname = nickname;
        player.loggedIn = true;
        send(player, JsonProtocol.loginResult(true, "注册成功", userId));
    }

    private void startMatch(PlayerConnection player) {
        if (player.room != null || waitingPlayers.contains(player)) {
            return;
        }
        PlayerConnection opponent = waitingPlayers.poll();
        if (opponent == null || opponent.conn.isClosed()) {
            waitingPlayers.add(player);
            return;
        }
        Room room = createRoom(player, opponent);
        room.start();
    }

    private Room createRoom(PlayerConnection first, PlayerConnection second) {
        PlayerConnection red = chooseRed(first, second);
        PlayerConnection black = red == first ? second : first;
        Room room = new Room("room-" + shortId(), red, black, new GameBoard(random.nextLong()));
        red.room = room;
        black.room = room;
        red.side = Piece.Side.RED;
        black.side = Piece.Side.BLACK;
        return room;
    }

    private PlayerConnection chooseRed(PlayerConnection first, PlayerConnection second) {
        if (first.wannaFirst && !second.wannaFirst) {
            return first;
        }
        if (second.wannaFirst && !first.wannaFirst) {
            return second;
        }
        return random.nextBoolean() ? first : second;
    }

    private void cancelMatch(PlayerConnection player) {
        waitingPlayers.remove(player);
    }

    private void handleReady(PlayerConnection player) {
        if (player.room == null) {
            send(player, JsonProtocol.error(3001, "房间不存在"));
            return;
        }
        player.ready = true;
        PlayerConnection opponent = player.room.opponent(player);
        send(opponent, JsonProtocol.roomInfo(true));
    }

    private void handleResign(PlayerConnection player) {
        if (player.room == null) {
            return;
        }
        PlayerConnection winner = player.room.opponent(player);
        player.room.broadcast(JsonProtocol.gameOver(winner.side, "resign", winner.userId));
        player.room.close();
    }

    private void handleMove(PlayerConnection player, JsonObject json) {
        if (player.room == null) {
            send(player, JsonProtocol.error(3001, "房间不存在"));
            return;
        }
        player.room.handleMove(player, JsonProtocol.toMove(json));
    }

    private void send(PlayerConnection player, JsonObject json) {
        if (player != null && player.conn != null && player.conn.isOpen()) {
            player.conn.send(json.toString());
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Account(String password, String nickname) {
    }

    private final class PlayerConnection {
        private final WebSocket conn;
        private String userId;
        private String nickname;
        private boolean loggedIn;
        private boolean wannaFirst;
        private boolean ready;
        private Room room;
        private Piece.Side side;

        private PlayerConnection(WebSocket conn, String userId, String nickname) {
            this.conn = conn;
            this.userId = userId;
            this.nickname = nickname;
        }
    }

    private final class Room {
        private final String roomId;
        private final PlayerConnection red;
        private final PlayerConnection black;
        private final GameBoard board;
        private Piece.Side turn = Piece.Side.RED;
        private boolean closed;
        private ScheduledFuture<?> timeoutTask;
        private final Map<String, Integer> checkingPositionCounts = new HashMap<>();
        private Piece.Side repeatedCheckingSide;

        private Room(String roomId, PlayerConnection red, PlayerConnection black, GameBoard board) {
            this.roomId = roomId;
            this.red = red;
            this.black = black;
            this.board = board;
        }

        private void start() {
            send(red, JsonProtocol.matchSuccess(roomId, black.userId, black.nickname));
            send(black, JsonProtocol.matchSuccess(roomId, red.userId, red.nickname));
            send(red, JsonProtocol.gameStart(red.userId, black.userId, Piece.Side.RED, true, board));
            send(black, JsonProtocol.gameStart(red.userId, black.userId, Piece.Side.BLACK, false, board));
            scheduleTurnTimeout();
        }

        private void handleMove(PlayerConnection player, Move move) {
            if (closed) {
                return;
            }
            if (player.side != turn) {
                send(player, JsonProtocol.error(2002, "未轮到本方走子"));
                send(player, JsonProtocol.moveResult(move, false, false));
                return;
            }
            if (!ruleEngine.isLegalMove(board, move, player.side)) {
                send(player, JsonProtocol.error(2001, "非法走子"));
                send(player, JsonProtocol.moveResult(move, false, false));
                return;
            }
            applyMove(move);
            broadcast(JsonProtocol.moveResult(move, true, true));
            Piece.Side winner = winnerAfterMove();
            if (winner != null) {
                PlayerConnection winnerPlayer = winner == Piece.Side.RED ? red : black;
                broadcast(JsonProtocol.gameOver(winner, "checkmate", winnerPlayer.userId));
                close();
                return;
            }
            if (isPerpetualCheckViolation(player.side)) {
                PlayerConnection winnerPlayer = opponent(player);
                broadcast(JsonProtocol.gameOver(winnerPlayer.side, "resign", winnerPlayer.userId, "perpetual_check"));
                close();
                return;
            }
            turn = turn == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
            scheduleTurnTimeout();
        }

        private void scheduleTurnTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
            }
            timeoutTask = scheduler.schedule(() -> {
                synchronized (GameServer.this) {
                    if (closed) {
                        return;
                    }
                    PlayerConnection loser = turn == Piece.Side.RED ? red : black;
                    PlayerConnection winner = opponent(loser);
                    broadcast(JsonProtocol.timeout(loser.userId, winner.userId));
                    close();
                }
            }, TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
            move.setType(source.getType());
            if (sr != dr || sc != dc) {
                board.set(dr, dc, source);
                board.set(sr, sc, null);
            }
        }

        private Piece.Side winnerAfterMove() {
            if (ruleEngine.isGeneralCaptured(board, Piece.Side.RED)) {
                return Piece.Side.BLACK;
            }
            if (ruleEngine.isGeneralCaptured(board, Piece.Side.BLACK)) {
                return Piece.Side.RED;
            }
            Piece.Side nextTurn = turn == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
            if (!ruleEngine.hasAnyLegalMove(board, nextTurn)) {
                return turn;
            }
            return null;
        }

        private boolean isPerpetualCheckViolation(Piece.Side checkingSide) {
            Piece.Side checkedSide = opposite(checkingSide);
            if (!ruleEngine.isInCheck(board, checkedSide)) {
                if (repeatedCheckingSide == checkingSide) {
                    checkingPositionCounts.clear();
                    repeatedCheckingSide = null;
                }
                return false;
            }

            if (repeatedCheckingSide != checkingSide) {
                checkingPositionCounts.clear();
                repeatedCheckingSide = checkingSide;
            }

            String signature = boardSignature(checkedSide);
            int count = checkingPositionCounts.merge(signature, 1, Integer::sum);
            return count >= REPEATED_CHECK_LIMIT;
        }

        private String boardSignature(Piece.Side nextSide) {
            StringBuilder builder = new StringBuilder();
            builder.append("next=").append(nextSide).append(';');
            for (int row = 0; row < GameBoard.ROWS; row++) {
                for (int col = 0; col < GameBoard.COLS; col++) {
                    Piece piece = board.get(row, col);
                    if (piece == null) {
                        builder.append('.');
                        continue;
                    }
                    builder.append(piece.getSide() == Piece.Side.RED ? 'R' : 'B')
                            .append(piece.getType())
                            .append(piece.isRevealed() ? '1' : '0');
                }
                builder.append('/');
            }
            return builder.toString();
        }

        private Piece.Side opposite(Piece.Side side) {
            return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
        }

        private PlayerConnection opponent(PlayerConnection player) {
            return player == red ? black : red;
        }

        private void broadcast(JsonObject json) {
            send(red, json);
            send(black, json);
        }

        private void finishByDisconnect(PlayerConnection disconnected) {
            if (closed) {
                return;
            }
            PlayerConnection winner = opponent(disconnected);
            if (winner != null && winner.conn.isOpen()) {
                send(winner, JsonProtocol.gameOver(winner.side, "resign", winner.userId, "disconnect"));
            }
            close();
        }

        private void close() {
            closed = true;
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
                timeoutTask = null;
            }
            red.room = null;
            black.room = null;
        }
    }
}
