package org.example.net;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class GameServer {
    private static final int PORT = 5000;

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        System.out.println("Server listening on " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket red = serverSocket.accept();
                Socket black = serverSocket.accept();
                long seed = new Random().nextLong();
                Thread gameThread = new Thread(new GameSession(red, black, seed));
                gameThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class GameSession implements Runnable {
        private final Socket redSocket;
        private final Socket blackSocket;
        private final GameBoard board;
        private final RuleEngine ruleEngine = new RuleEngine();
        private final long seed;

        GameSession(Socket redSocket, Socket blackSocket, long seed) {
            this.redSocket = redSocket;
            this.blackSocket = blackSocket;
            this.seed = seed;
            this.board = new GameBoard(seed);
        }

        @Override
        public void run() {
            try (Socket red = redSocket; Socket black = blackSocket;
                 ObjectOutputStream redOut = new ObjectOutputStream(red.getOutputStream());
                ObjectOutputStream blackOut = new ObjectOutputStream(black.getOutputStream());
                ObjectInputStream redIn = new ObjectInputStream(red.getInputStream());
                ObjectInputStream blackIn = new ObjectInputStream(black.getInputStream())) {

                redOut.writeObject("COLOR:RED");
                redOut.writeObject("SEED:" + seed);
                redOut.flush();
                blackOut.writeObject("COLOR:BLACK");
                blackOut.writeObject("SEED:" + seed);
                blackOut.flush();

                Piece.Side turn = Piece.Side.RED;
                while (true) {
                    ObjectInputStream currentIn = turn == Piece.Side.RED ? redIn : blackIn;
                    ObjectOutputStream currentOut = turn == Piece.Side.RED ? redOut : blackOut;
                    Object obj = currentIn.readObject();
                    if (!(obj instanceof Move move)) {
                        continue;
                    }
                    if (handleMove(move, turn, currentOut, redOut, blackOut)) {
                        turn = turn == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
                    }
                }
            } catch (Exception e) {
                System.out.println("Game ended: " + e.getMessage());
            }
        }

        private boolean handleMove(Move move, Piece.Side turn, ObjectOutputStream currentOut,
                                   ObjectOutputStream redOut, ObjectOutputStream blackOut) throws Exception {
            int sr = board.rowFromCoord(move.getSource());
            int sc = board.colFromCoord(move.getSource());
            Piece source = board.get(sr, sc);
            if (source == null || source.getSide() != turn) {
                currentOut.writeObject("ERROR:invalid source");
                currentOut.flush();
                return false;
            }
            if (!ruleEngine.isLegalMove(board, move, turn)) {
                currentOut.writeObject("ERROR:illegal move");
                currentOut.flush();
                return false;
            }
            int dr = board.rowFromCoord(move.getDestination());
            int dc = board.colFromCoord(move.getDestination());
            if (sr == dr && sc == dc) {
                source.setRevealed(true);
                move.setType(source.getType());
            } else {
                Piece target = board.get(dr, dc);
                if (target != null && target.getSide() != source.getSide()) {
                    board.set(dr, dc, null);
                }
                board.set(dr, dc, source);
                board.set(sr, sc, null);
                source.setRevealed(true);
                move.setType(source.getType());
            }
            redOut.writeObject(move);
            redOut.flush();
            blackOut.writeObject(move);
            blackOut.flush();
            return true;
        }
    }
}
