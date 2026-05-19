package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSnapshotFormatterTest {
    @Test
    void includesSideBoardAndCandidateIds() {
        GameBoard board = new GameBoard(7L);
        List<CandidateMove> candidates = List.of(
                new CandidateMove("C001", new Move("a6", "a5", null, 0L), MoveKind.MOVE, "RED_HIDDEN", "EMPTY", 10)
        );

        String prompt = new BoardSnapshotFormatter().format(board, Piece.Side.RED, candidates, false);

        assertTrue(prompt.contains("side=RED"));
        assertTrue(prompt.contains("check=false"));
        assertTrue(prompt.contains("C001"));
        assertTrue(prompt.contains("a6"));
    }
}
