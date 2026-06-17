package org.example.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameBoard {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final Piece[][] board = new Piece[ROWS][COLS];
    private final Random random;

    public GameBoard(long seed) {
        this.random = new Random(seed);
        reset();
    }

    public void reset() {
        clear();
        placeSide(Piece.Side.BLACK, 0);
        placeSide(Piece.Side.RED, 9);
    }

    public void clear() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = null;
            }
        }
    }

    private void placeSide(Piece.Side side, int generalRow) {
        List<Integer> types = new ArrayList<>();
        Collections.addAll(types, 1, 1, 2, 2, 3, 3, 4, 4, 4, 4, 4, 5, 5, 6, 6);
        Collections.shuffle(types, random);

        List<int[]> spots = new ArrayList<>();
        int backRow = generalRow;
        int cannonRow = side == Piece.Side.BLACK ? 2 : 7;
        int pawnRow = side == Piece.Side.BLACK ? 3 : 6;
        int generalCol = 4;
        int[] backCols = {0, 1, 2, 3, 5, 6, 7, 8};
        int[] cannonCols = {1, 7};
        int[] pawnCols = {0, 2, 4, 6, 8};

        for (int col : backCols) {
            spots.add(new int[]{backRow, col});
        }
        for (int col : cannonCols) {
            spots.add(new int[]{cannonRow, col});
        }
        for (int col : pawnCols) {
            spots.add(new int[]{pawnRow, col});
        }
        Collections.shuffle(spots, random);

        board[generalRow][generalCol] = new Piece(side, 0, true);

        for (int i = 0; i < types.size(); i++) {
            int[] pos = spots.get(i);
            board[pos[0]][pos[1]] = new Piece(side, types.get(i), false);
        }
    }

    public Piece get(int row, int col) {
        return inBounds(row, col) ? board[row][col] : null;
    }

    public void set(int row, int col, Piece piece) {
        if (inBounds(row, col)) {
            board[row][col] = piece;
        }
    }

    public boolean inBounds(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    public int rowFromCoord(String coord) {
        if (coord == null || coord.length() < 2) {
            return -1;
        }
        return Character.getNumericValue(coord.charAt(1));
    }

    public int colFromCoord(String coord) {
        if (coord == null || coord.length() < 2) {
            return -1;
        }
        return coord.charAt(0) - 'a';
    }

    public String coord(int row, int col) {
        return String.valueOf((char) ('a' + col)) + row;
    }

    public int positionType(int row, int col) {
        if (row == 0 || row == 9) {
            return switch (col) {
                case 0, 8 -> 1;
                case 1, 7 -> 2;
                case 2, 6 -> 6;
                case 3, 5 -> 5;
                case 4 -> 0;
                default -> -1;
            };
        }
        if (row == 2 || row == 7) {
            return (col == 1 || col == 7) ? 3 : -1;
        }
        if (row == 3 || row == 6) {
            return (col % 2 == 0) ? 4 : -1;
        }
        return -1;
    }
}
