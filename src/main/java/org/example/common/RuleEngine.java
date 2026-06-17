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

        Piece target = board.get(dr, dc);
        if (target != null && target.getSide() == side) {
            return false;
        }

        boolean flipOnly = move.isFlip();
        if (flipOnly && source.isRevealed()) {
            return false;
        }

        int movingType = source.isRevealed() ? source.getType() : board.positionType(sr, sc);
        if (!flipOnly && !canMoveByRule(board, sr, sc, dr, dc, source, target, movingType, source.isRevealed())) {
            return false;
        }

        return isSafeAfterMove(board, sr, sc, dr, dc, side, flipOnly);
    }

    public boolean isGeneralCaptured(GameBoard board, Piece.Side side) {
        return findGeneral(board, side) == null;
    }

    public boolean hasAnyLegalMove(GameBoard board, Piece.Side side) {
        for (int sr = 0; sr < GameBoard.ROWS; sr++) {
            for (int sc = 0; sc < GameBoard.COLS; sc++) {
                Piece piece = board.get(sr, sc);
                if (piece == null || piece.getSide() != side) {
                    continue;
                }
                if (!piece.isRevealed() && isLegalMove(board,
                        new Move(board.coord(sr, sc), board.coord(sr, sc), null, 0L), side)) {
                    return true;
                }
                for (int dr = 0; dr < GameBoard.ROWS; dr++) {
                    for (int dc = 0; dc < GameBoard.COLS; dc++) {
                        if (sr == dr && sc == dc) {
                            continue;
                        }
                        if (isLegalMove(board,
                                new Move(board.coord(sr, sc), board.coord(dr, dc), null, 0L), side)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isSafeAfterMove(GameBoard board, int sr, int sc, int dr, int dc, Piece.Side side, boolean flipOnly) {
        Piece source = board.get(sr, sc);
        Piece target = board.get(dr, dc);
        boolean originalReveal = source.isRevealed();

        if (flipOnly) {
            source.setRevealed(true);
        } else {
            board.set(dr, dc, source);
            board.set(sr, sc, null);
            source.setRevealed(true);
        }

        boolean safe = !isInCheck(board, side) && !generalsFacing(board);

        if (flipOnly) {
            source.setRevealed(originalReveal);
        } else {
            source.setRevealed(originalReveal);
            board.set(sr, sc, source);
            board.set(dr, dc, target);
        }
        return safe;
    }

    public boolean isInCheck(GameBoard board, Piece.Side side) {
        int[] generalPos = findGeneral(board, side);
        if (generalPos == null) {
            return true;
        }

        Piece.Side enemy = side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                Piece piece = board.get(r, c);
                if (piece == null || piece.getSide() != enemy) {
                    continue;
                }
                int movingType = piece.isRevealed() ? piece.getType() : board.positionType(r, c);
                if (canMoveByRule(board, r, c, generalPos[0], generalPos[1], piece,
                        board.get(generalPos[0], generalPos[1]), movingType, piece.isRevealed())) {
                    return true;
                }
            }
        }

        return generalsFacing(board);
    }

    private boolean generalsFacing(GameBoard board) {
        int[] red = findGeneral(board, Piece.Side.RED);
        int[] black = findGeneral(board, Piece.Side.BLACK);
        if (red == null || black == null || red[1] != black[1]) {
            return false;
        }
        int col = red[1];
        int start = Math.min(red[0], black[0]) + 1;
        int end = Math.max(red[0], black[0]);
        for (int row = start; row < end; row++) {
            if (board.get(row, col) != null) {
                return false;
            }
        }
        return true;
    }

    private int[] findGeneral(GameBoard board, Piece.Side side) {
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                Piece piece = board.get(r, c);
                if (piece != null && piece.getSide() == side && piece.getType() == 0) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private boolean canMoveByRule(GameBoard board, int sr, int sc, int dr, int dc, Piece source, Piece target,
                                  int movingType, boolean revealedMovement) {
        return switch (movingType) {
            case 0 -> canKingMove(board, sr, sc, dr, dc, source.getSide(), target);
            case 1 -> sameLine(sr, sc, dr, dc) && clearLine(board, sr, sc, dr, dc);
            case 2 -> canKnightMove(board, sr, sc, dr, dc);
            case 3 -> canCannonMove(board, sr, sc, dr, dc, target != null);
            case 4 -> canSoldierMove(sr, sc, dr, dc, source.getSide());
            case 5 -> canAdvisorMove(sr, sc, dr, dc, source.getSide(), revealedMovement);
            case 6 -> canElephantMove(board, sr, sc, dr, dc, source.getSide(), revealedMovement);
            default -> false;
        };
    }

    private boolean canKingMove(GameBoard board, int sr, int sc, int dr, int dc, Piece.Side side, Piece target) {
        if (target != null && target.getType() == 0 && target.getSide() != side && sc == dc && clearLine(board, sr, sc, dr, dc)) {
            return true;
        }
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

    private boolean canAdvisorMove(int sr, int sc, int dr, int dc, Piece.Side side, boolean revealedMovement) {
        if (Math.abs(sr - dr) != 1 || Math.abs(sc - dc) != 1) {
            return false;
        }
        if (revealedMovement) {
            return true;
        }
        if (side == Piece.Side.RED) {
            return dr >= 7 && dr <= 9 && dc >= 3 && dc <= 5;
        }
        return dr >= 0 && dr <= 2 && dc >= 3 && dc <= 5;
    }

    private boolean canElephantMove(GameBoard board, int sr, int sc, int dr, int dc, Piece.Side side, boolean revealedMovement) {
        if (Math.abs(sr - dr) != 2 || Math.abs(sc - dc) != 2) {
            return false;
        }
        int eyeRow = (sr + dr) / 2;
        int eyeCol = (sc + dc) / 2;
        if (board.get(eyeRow, eyeCol) != null) {
            return false;
        }
        if (revealedMovement) {
            return true;
        }
        return side == Piece.Side.RED ? dr >= 5 : dr <= 4;
    }

    private boolean canSoldierMove(int sr, int sc, int dr, int dc, Piece.Side side) {
        int forward = side == Piece.Side.RED ? -1 : 1;
        boolean crossedRiver = side == Piece.Side.RED ? sr <= 4 : sr >= 5;
        if (dc == sc && dr - sr == forward) {
            return true;
        }
        return crossedRiver && Math.abs(dc - sc) == 1 && dr == sr;
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
