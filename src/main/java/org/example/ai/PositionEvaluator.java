package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Piece;
import org.example.common.RuleEngine;

public class PositionEvaluator {
    private static final int WIN_SCORE = 1_000_000;
    private final RuleEngine ruleEngine;

    public PositionEvaluator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public int evaluate(GameBoard board, Piece.Side perspective) {
        Piece.Side enemy = opposite(perspective);
        if (ruleEngine.isGeneralCaptured(board, perspective)) {
            return -WIN_SCORE;
        }
        if (ruleEngine.isGeneralCaptured(board, enemy)) {
            return WIN_SCORE;
        }

        int score = 0;
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                if (piece == null) {
                    continue;
                }
                int value = pieceValue(piece.getType())
                        + pieceSquareBonus(board, row, col, piece)
                        + revealBonus(piece);
                score += piece.getSide() == perspective ? value : -value;
            }
        }

        if (ruleEngine.isInCheck(board, perspective)) {
            score -= 600;
        }
        if (ruleEngine.isInCheck(board, enemy)) {
            score += 600;
        }
        score += kingSafety(board, perspective);
        score -= kingSafety(board, enemy);
        score += (pseudoMobility(board, perspective) - pseudoMobility(board, enemy)) * 6;
        return score;
    }

    private int pieceSquareBonus(GameBoard board, int row, int col, Piece piece) {
        int centerDistance = Math.abs(row - 4) + Math.abs(col - 4);
        int progress = piece.getSide() == Piece.Side.RED ? 9 - row : row;
        return switch (piece.getType()) {
            case 0 -> kingPositionBonus(row, col, piece.getSide());
            case 1 -> 90 - centerDistance * 6 + lineActivity(board, row, col) * 12;
            case 2 -> 80 - centerDistance * 12 + progress * 5;
            case 3 -> 70 - centerDistance * 7 + lineActivity(board, row, col) * 10;
            case 4 -> pawnBonus(row, col, piece.getSide(), progress);
            case 5 -> 35 - centerDistance * 4 + defenseZoneBonus(row, col, piece.getSide());
            case 6 -> 35 - centerDistance * 3 + defenseZoneBonus(row, col, piece.getSide());
            default -> 0;
        };
    }

    private int revealBonus(Piece piece) {
        return piece.isRevealed() ? 18 : -25;
    }

    private int kingPositionBonus(int row, int col, Piece.Side side) {
        int homeRow = side == Piece.Side.RED ? 9 : 0;
        int palaceDistance = Math.abs(row - homeRow) + Math.abs(col - 4);
        return 120 - palaceDistance * 35;
    }

    private int pawnBonus(int row, int col, Piece.Side side, int progress) {
        int score = progress * 22 - Math.abs(col - 4) * 5;
        boolean crossedRiver = side == Piece.Side.RED ? row <= 4 : row >= 5;
        if (crossedRiver) {
            score += 90;
        }
        return score;
    }

    private int defenseZoneBonus(int row, int col, Piece.Side side) {
        int homeRow = side == Piece.Side.RED ? 9 : 0;
        int distance = Math.abs(row - homeRow) + Math.abs(col - 4);
        return Math.max(0, 50 - distance * 12);
    }

    private int lineActivity(GameBoard board, int row, int col) {
        int activity = 0;
        activity += rayActivity(board, row, col, 1, 0);
        activity += rayActivity(board, row, col, -1, 0);
        activity += rayActivity(board, row, col, 0, 1);
        activity += rayActivity(board, row, col, 0, -1);
        return activity;
    }

    private int rayActivity(GameBoard board, int row, int col, int rowStep, int colStep) {
        int activity = 0;
        Piece source = board.get(row, col);
        int r = row + rowStep;
        int c = col + colStep;
        while (board.inBounds(r, c)) {
            Piece target = board.get(r, c);
            if (target == null) {
                activity++;
            } else {
                if (source != null && target.getSide() != source.getSide()) {
                    activity += 2;
                }
                break;
            }
            r += rowStep;
            c += colStep;
        }
        return activity;
    }

    private int kingSafety(GameBoard board, Piece.Side side) {
        int[] king = findGeneral(board, side);
        if (king == null) {
            return -WIN_SCORE;
        }
        int safety = 0;
        for (int row = Math.max(0, king[0] - 2); row <= Math.min(GameBoard.ROWS - 1, king[0] + 2); row++) {
            for (int col = Math.max(0, king[1] - 2); col <= Math.min(GameBoard.COLS - 1, king[1] + 2); col++) {
                Piece piece = board.get(row, col);
                if (piece != null && piece.getSide() == side && piece.getType() != 0) {
                    safety += 18;
                }
            }
        }
        return safety;
    }

    private int pseudoMobility(GameBoard board, Piece.Side side) {
        int mobility = 0;
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                if (piece == null || piece.getSide() != side) {
                    continue;
                }
                if (!piece.isRevealed()) {
                    mobility += 2;
                }
                mobility += switch (piece.getType()) {
                    case 0 -> localMobility(board, row, col, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
                    case 1 -> lineActivity(board, row, col) * 2;
                    case 2 -> knightMobility(board, row, col);
                    case 3 -> lineActivity(board, row, col) * 2;
                    case 4 -> pawnMobility(board, row, col, side);
                    case 5 -> localMobility(board, row, col, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
                    case 6 -> elephantMobility(board, row, col);
                    default -> 0;
                };
            }
        }
        return Math.min(80, mobility);
    }

    private int localMobility(GameBoard board, int row, int col, int[][] deltas) {
        int count = 0;
        Piece source = board.get(row, col);
        for (int[] delta : deltas) {
            int nextRow = row + delta[0];
            int nextCol = col + delta[1];
            if (!board.inBounds(nextRow, nextCol)) {
                continue;
            }
            Piece target = board.get(nextRow, nextCol);
            if (target == null || source == null || target.getSide() != source.getSide()) {
                count++;
            }
        }
        return count;
    }

    private int knightMobility(GameBoard board, int row, int col) {
        int[][] jumps = {
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
        };
        int count = 0;
        Piece source = board.get(row, col);
        for (int[] jump : jumps) {
            int targetRow = row + jump[0];
            int targetCol = col + jump[1];
            if (!board.inBounds(targetRow, targetCol)) {
                continue;
            }
            int legRow = Math.abs(jump[0]) == 2 ? row + Integer.signum(jump[0]) : row;
            int legCol = Math.abs(jump[1]) == 2 ? col + Integer.signum(jump[1]) : col;
            Piece target = board.get(targetRow, targetCol);
            if (board.get(legRow, legCol) == null
                    && (target == null || source == null || target.getSide() != source.getSide())) {
                count++;
            }
        }
        return count;
    }

    private int pawnMobility(GameBoard board, int row, int col, Piece.Side side) {
        int forward = side == Piece.Side.RED ? -1 : 1;
        int count = targetAvailable(board, row, col, row + forward, col) ? 1 : 0;
        boolean crossedRiver = side == Piece.Side.RED ? row <= 4 : row >= 5;
        if (crossedRiver) {
            count += targetAvailable(board, row, col, row, col - 1) ? 1 : 0;
            count += targetAvailable(board, row, col, row, col + 1) ? 1 : 0;
        }
        return count;
    }

    private int elephantMobility(GameBoard board, int row, int col) {
        int count = 0;
        int[][] jumps = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
        for (int[] jump : jumps) {
            int targetRow = row + jump[0];
            int targetCol = col + jump[1];
            int eyeRow = row + jump[0] / 2;
            int eyeCol = col + jump[1] / 2;
            if (board.inBounds(targetRow, targetCol)
                    && board.get(eyeRow, eyeCol) == null
                    && targetAvailable(board, row, col, targetRow, targetCol)) {
                count++;
            }
        }
        return count;
    }

    private boolean targetAvailable(GameBoard board, int row, int col, int targetRow, int targetCol) {
        if (!board.inBounds(targetRow, targetCol)) {
            return false;
        }
        Piece source = board.get(row, col);
        Piece target = board.get(targetRow, targetCol);
        return target == null || source == null || target.getSide() != source.getSide();
    }

    private int[] findGeneral(GameBoard board, Piece.Side side) {
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                if (piece != null && piece.getSide() == side && piece.getType() == 0) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    private int pieceValue(int type) {
        return switch (type) {
            case 0 -> 10_000;
            case 1 -> 900;
            case 2 -> 450;
            case 3 -> 500;
            case 4 -> 120;
            case 5 -> 200;
            case 6 -> 220;
            default -> 0;
        };
    }

    private Piece.Side opposite(Piece.Side side) {
        return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
    }
}
