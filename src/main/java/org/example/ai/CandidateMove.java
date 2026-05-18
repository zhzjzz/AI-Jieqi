package org.example.ai;

import org.example.common.Move;

public record CandidateMove(
        String id,
        Move move,
        MoveKind kind,
        String pieceLabel,
        String targetLabel,
        int heuristicScore
) {
    public CandidateMove withHeuristicScore(int score) {
        return new CandidateMove(id, move, kind, pieceLabel, targetLabel, score);
    }
}
