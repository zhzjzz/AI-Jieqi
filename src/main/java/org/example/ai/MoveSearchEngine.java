package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MoveSearchEngine {
    private static final int SEARCH_DEPTH = 3;
    private static final int BRANCH_LIMIT = 12;
    private static final int WIN_SCORE = 1_000_000;
    private static final int NEG_INF = Integer.MIN_VALUE / 4;
    private static final int POS_INF = Integer.MAX_VALUE / 4;

    private final RuleEngine ruleEngine;
    private final LegalMoveGenerator moveGenerator;
    private final HeuristicMoveSelector heuristicSelector;
    private final PositionEvaluator evaluator;

    public MoveSearchEngine(RuleEngine ruleEngine,
                            LegalMoveGenerator moveGenerator,
                            HeuristicMoveSelector heuristicSelector,
                            PositionEvaluator evaluator) {
        this.ruleEngine = ruleEngine;
        this.moveGenerator = moveGenerator;
        this.heuristicSelector = heuristicSelector;
        this.evaluator = evaluator;
    }

    public List<SearchAnalysis> rank(GameBoard board, Piece.Side side) {
        List<CandidateMove> ordered = heuristicSelector.score(board, side, moveGenerator.generate(board, side));
        List<SearchAnalysis> analyses = new ArrayList<>();
        for (CandidateMove candidate : ordered) {
            GameBoard next = copyBoard(board);
            applyMove(next, candidate.move());
            int score = alphaBeta(next, opposite(side), side, SEARCH_DEPTH - 1, NEG_INF, POS_INF);
            analyses.add(new SearchAnalysis(candidate, score));
        }
        analyses.sort(Comparator.comparingInt(SearchAnalysis::searchScore).reversed()
                .thenComparing(analysis -> analysis.candidate().heuristicScore(), Comparator.reverseOrder())
                .thenComparing(analysis -> analysis.candidate().id()));
        return analyses;
    }

    private int alphaBeta(GameBoard board, Piece.Side sideToMove, Piece.Side perspective, int depth, int alpha, int beta) {
        Piece.Side enemy = opposite(perspective);
        if (ruleEngine.isGeneralCaptured(board, perspective)) {
            return -WIN_SCORE + depth;
        }
        if (ruleEngine.isGeneralCaptured(board, enemy)) {
            return WIN_SCORE - depth;
        }
        if (depth <= 0) {
            return evaluator.evaluate(board, perspective);
        }

        List<CandidateMove> candidates = heuristicSelector.score(board, sideToMove, moveGenerator.generate(board, sideToMove));
        if (candidates.isEmpty()) {
            return sideToMove == perspective ? -WIN_SCORE + depth : WIN_SCORE - depth;
        }

        int limit = Math.min(BRANCH_LIMIT, candidates.size());
        if (sideToMove == perspective) {
            int best = NEG_INF;
            for (int i = 0; i < limit; i++) {
                CandidateMove candidate = candidates.get(i);
                GameBoard next = copyBoard(board);
                applyMove(next, candidate.move());
                int score = alphaBeta(next, opposite(sideToMove), perspective, depth - 1, alpha, beta);
                if (score > best) {
                    best = score;
                }
                if (score > alpha) {
                    alpha = score;
                }
                if (alpha >= beta) {
                    break;
                }
            }
            return best;
        }

        int best = POS_INF;
        for (int i = 0; i < limit; i++) {
            CandidateMove candidate = candidates.get(i);
            GameBoard next = copyBoard(board);
            applyMove(next, candidate.move());
            int score = alphaBeta(next, opposite(sideToMove), perspective, depth - 1, alpha, beta);
            if (score < best) {
                best = score;
            }
            if (score < beta) {
                beta = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
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
