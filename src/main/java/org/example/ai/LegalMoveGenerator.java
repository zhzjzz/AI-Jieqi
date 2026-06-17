package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.ArrayList;
import java.util.List;

public class LegalMoveGenerator {
    private final RuleEngine ruleEngine;

    public LegalMoveGenerator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public List<CandidateMove> generate(GameBoard board, Piece.Side side) {
        List<CandidateMove> candidates = new ArrayList<>();
        int sequence = 1;
        for (int sr = 0; sr < GameBoard.ROWS; sr++) {
            for (int sc = 0; sc < GameBoard.COLS; sc++) {
                Piece source = board.get(sr, sc);
                if (source == null || source.getSide() != side) {
                    continue;
                }
                if (!source.isRevealed()) {
                    Move reveal = new Move(board.coord(sr, sc), board.coord(sr, sc), null, 0L);
                    if (ruleEngine.isLegalMove(board, reveal, side)) {
                        candidates.add(candidate(board, reveal, MoveKind.REVEAL, sequence++));
                    }
                }
                for (int dr = 0; dr < GameBoard.ROWS; dr++) {
                    for (int dc = 0; dc < GameBoard.COLS; dc++) {
                        if (sr == dr && sc == dc) {
                            continue;
                        }
                        Move move = new Move(board.coord(sr, sc), board.coord(dr, dc), null, 0L);
                        if (ruleEngine.isLegalMove(board, move, side)) {
                            candidates.add(candidate(board, move, classify(board, dr, dc), sequence++));
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private CandidateMove candidate(GameBoard board, Move move, MoveKind kind, int sequence) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        Piece target = board.get(dr, dc);
        return new CandidateMove(
                "C" + String.format("%03d", sequence),
                move,
                kind,
                label(source),
                target == null ? "EMPTY" : label(target),
                0
        );
    }

    private MoveKind classify(GameBoard board, int row, int col) {
        Piece target = board.get(row, col);
        if (target == null) {
            return MoveKind.MOVE;
        }
        return target.getType() == 0 ? MoveKind.CAPTURE_GENERAL : MoveKind.CAPTURE;
    }

    private String label(Piece piece) {
        if (piece == null) {
            return "EMPTY";
        }
        String side = piece.getSide() == Piece.Side.RED ? "RED" : "BLACK";
        return side + "_" + (piece.isRevealed() ? piece.shortName() : "HIDDEN");
    }
}
