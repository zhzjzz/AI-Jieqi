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
                int value = pieceValue(piece.getType());
                score += piece.getSide() == perspective ? value : -value;
                if (piece.isRevealed()) {
                    score += piece.getSide() == perspective ? 5 : -5;
                }
            }
        }

        if (ruleEngine.isInCheck(board, perspective)) {
            score -= 80;
        }
        if (ruleEngine.isInCheck(board, enemy)) {
            score += 80;
        }
        return score;
    }

    private int pieceValue(int type) {
        return switch (type) {
            case 0 -> 10_000;
            case 1 -> 900;
            case 2 -> 450;
            case 3 -> 500;
            case 4 -> 120;
            case 5 -> 200;
            case 6 -> 200;
            default -> 0;
        };
    }

    private Piece.Side opposite(Piece.Side side) {
        return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
    }
}
