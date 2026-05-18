package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HeuristicMoveSelector {
    private static final int WIN_SCORE = 1_000_000;
    private final RuleEngine ruleEngine;
    private final PositionEvaluator evaluator;
    private final LegalMoveGenerator moveGenerator;

    public HeuristicMoveSelector(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
        this.evaluator = new PositionEvaluator(ruleEngine);
        this.moveGenerator = new LegalMoveGenerator(ruleEngine);
    }

    public List<CandidateMove> score(GameBoard board, Piece.Side side, List<CandidateMove> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.withHeuristicScore(score(board, side, candidate)))
                .sorted(Comparator.comparingInt(CandidateMove::heuristicScore).reversed()
                        .thenComparing(CandidateMove::id))
                .toList();
    }

    public Optional<CandidateMove> select(GameBoard board, Piece.Side side, List<CandidateMove> candidates) {
        return score(board, side, candidates).stream().findFirst();
    }

    private int score(GameBoard board, Piece.Side side, CandidateMove candidate) {
        Move move = candidate.move();
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        Piece target = board.get(dr, dc);

        GameBoard next = copyBoard(board);
        applyMove(next, move);

        Piece.Side enemy = opposite(side);
        int score = evaluator.evaluate(next, side);
        score += tacticalBonus(candidate, source, target);
        score += centerBias(dr, dc);

        if (ruleEngine.isGeneralCaptured(next, enemy)) {
            score += WIN_SCORE;
        }
        if (ruleEngine.isInCheck(next, enemy)) {
            score += 2_500;
        }
        if (isImmediatelyCapturable(next, dr, dc, source == null ? -1 : source.getType(), enemy)) {
            score -= pieceRiskValue(source);
        }
        if (candidate.kind() == MoveKind.REVEAL) {
            score += revealSafetyBonus(sr, sc, next, side);
        }

        int mobility = moveGenerator.generate(next, side).size() - moveGenerator.generate(next, enemy).size();
        score += mobility * 6;
        return score;
    }

    private int tacticalBonus(CandidateMove candidate, Piece source, Piece target) {
        return switch (candidate.kind()) {
            case CAPTURE_GENERAL -> WIN_SCORE;
            case CAPTURE -> captureSwing(source, target);
            case REVEAL -> 60;
            case MOVE -> 0;
        };
    }

    private int captureSwing(Piece source, Piece target) {
        int gain = pieceValue(target);
        int risk = source == null ? 0 : pieceValue(source) / 3;
        return 500 + gain - risk;
    }

    private int centerBias(int row, int col) {
        int rowDistance = Math.abs(row - 4);
        int colDistance = Math.abs(col - 4);
        return 30 - (rowDistance * 3 + colDistance * 4);
    }

    private int revealSafetyBonus(int row, int col, GameBoard next, Piece.Side side) {
        if (isImmediatelyCapturable(next, row, col, next.positionType(row, col), opposite(side))) {
            return -120;
        }
        return 80;
    }

    private boolean isImmediatelyCapturable(GameBoard board, int row, int col, int movingType, Piece.Side attackerSide) {
        if (row < 0 || col < 0) {
            return false;
        }
        String targetCoord = board.coord(row, col);
        for (int sr = 0; sr < GameBoard.ROWS; sr++) {
            for (int sc = 0; sc < GameBoard.COLS; sc++) {
                Piece attacker = board.get(sr, sc);
                if (attacker == null || attacker.getSide() != attackerSide) {
                    continue;
                }
                Move response = new Move(board.coord(sr, sc), targetCoord, null, 0L);
                if (ruleEngine.isLegalMove(board, response, attackerSide)) {
                    return true;
                }
                if (!attacker.isRevealed() && board.positionType(sr, sc) == movingType) {
                    Move speculative = new Move(board.coord(sr, sc), targetCoord, null, 0L);
                    if (ruleEngine.isLegalMove(board, speculative, attackerSide)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int pieceRiskValue(Piece source) {
        if (source == null) {
            return 150;
        }
        return Math.max(180, pieceValue(source) / 2);
    }

    private int pieceValue(Piece piece) {
        if (piece == null) {
            return 0;
        }
        if (!piece.isRevealed()) {
            return 330;
        }
        return switch (piece.getType()) {
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

    private GameBoard copyBoard(GameBoard board) {
        GameBoard copy = new GameBoard(0L);
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                if (piece == null) {
                    copy.set(row, col, null);
                } else {
                    copy.set(row, col, new Piece(piece.getSide(), piece.getType(), piece.isRevealed()));
                }
            }
        }
        return copy;
    }

    private void applyMove(GameBoard board, Move move) {
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

    private Piece.Side opposite(Piece.Side side) {
        return side == Piece.Side.RED ? Piece.Side.BLACK : Piece.Side.RED;
    }
}
