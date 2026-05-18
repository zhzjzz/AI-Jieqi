package org.example.common;

public class RuleEngine {
    public boolean isLegalMove(GameBoard board, Move move, Piece.Side side) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        if (!board.inBounds(sr, sc) || !board.inBounds(dr, dc)) {
            return false;
        }
        Piece source = board.get(sr, sc);
        if (source == null || source.getSide() != side) {
            return false;
        }
        if (sr == dr && sc == dc) {
            return true;
        }
        Piece target = board.get(dr, dc);
        if (target != null && target.getSide() == side) {
            return false;
        }
        return switch (source.getType()) {
            case 0 -> canKingMove(sr, sc, dr, dc, source.getSide());
            case 1 -> sameLine(sr, sc, dr, dc) && clearLine(board, sr, sc, dr, dc);
            case 2 -> canKnightMove(board, sr, sc, dr, dc);
            case 3 -> canCannonMove(board, sr, sc, dr, dc, target != null);
            case 4 -> canSoldierMove(sr, sc, dr, dc, source.getSide());
            case 5 -> canAdvisorMove(sr, sc, dr, dc);
            case 6 -> canElephantMove(sr, sc, dr, dc);
            default -> false;
        };
    }

    private boolean canKingMove(int sr, int sc, int dr, int dc, Piece.Side side) {
        int rowDiff = Math.abs(sr - dr);
        int colDiff = Math.abs(sc - dc);
        if (rowDiff + colDiff != 1) {
            return false;
        }
        if (side == Piece.Side.RED) {
            return dr >= 7 && dr <= 9 && dc >= 3 && dc <= 5;
        }
        return dr >= 0 && dr <= 2 && dc >= 3 && dc <= 5;
    }

    private boolean canAdvisorMove(int sr, int sc, int dr, int dc) {
        return Math.abs(sr - dr) == 1 && Math.abs(sc - dc) == 1;
    }

    private boolean canElephantMove(int sr, int sc, int dr, int dc) {
        return Math.abs(sr - dr) == 2 && Math.abs(sc - dc) == 2;
    }

    private boolean canSoldierMove(int sr, int sc, int dr, int dc, Piece.Side side) {
        int forward = side == Piece.Side.RED ? -1 : 1;
        return (dc == sc && dr - sr == forward) || (Math.abs(dc - sc) == 1 && dr == sr);
    }

    private boolean canKnightMove(GameBoard board, int sr, int sc, int dr, int dc) {
        int rowDiff = Math.abs(sr - dr);
        int colDiff = Math.abs(sc - dc);
        if (!((rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2))) {
            return false;
        }
        if (rowDiff == 2) {
            int legRow = sr + Integer.signum(dr - sr);
            return board.get(legRow, sc) == null;
        }
        int legCol = sc + Integer.signum(dc - sc);
        return board.get(sr, legCol) == null;
    }

    private boolean canCannonMove(GameBoard board, int sr, int sc, int dr, int dc, boolean capture) {
        if (!sameLine(sr, sc, dr, dc)) {
            return false;
        }
        int between = countBetween(board, sr, sc, dr, dc);
        return capture ? between == 1 : between == 0;
    }

    private boolean sameLine(int sr, int sc, int dr, int dc) {
        return sr == dr || sc == dc;
    }

    private boolean clearLine(GameBoard board, int sr, int sc, int dr, int dc) {
        return countBetween(board, sr, sc, dr, dc) == 0;
    }

    private int countBetween(GameBoard board, int sr, int sc, int dr, int dc) {
        int count = 0;
        int rowStep = Integer.compare(dr, sr);
        int colStep = Integer.compare(dc, sc);
        int r = sr + rowStep;
        int c = sc + colStep;
        while (r != dr || c != dc) {
            if (board.get(r, c) != null) {
                count++;
            }
            r += rowStep;
            c += colStep;
        }
        return count;
    }
}
