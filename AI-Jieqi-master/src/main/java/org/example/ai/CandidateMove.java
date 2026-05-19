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
}
