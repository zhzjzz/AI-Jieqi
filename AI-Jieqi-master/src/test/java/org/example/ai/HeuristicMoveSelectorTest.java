package org.example.ai;

import org.example.common.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicMoveSelectorTest {
    @Test
    void prefersCapturingGeneral() {
        CandidateMove quiet = new CandidateMove("C001", new Move("a0", "a1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);
        CandidateMove win = new CandidateMove("C002", new Move("e1", "e0", null, 0L), MoveKind.CAPTURE_GENERAL, "RED_车", "BLACK_将", 0);

        Optional<CandidateMove> selected = new HeuristicMoveSelector().select(List.of(quiet, win));

        assertTrue(selected.isPresent());
        assertEquals("C002", selected.get().id());
        assertTrue(selected.get().heuristicScore() > quiet.heuristicScore());
    }

    @Test
    void usesStableIdOrderWhenScoresTie() {
        CandidateMove second = new CandidateMove("C002", new Move("b0", "b1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);
        CandidateMove first = new CandidateMove("C001", new Move("a0", "a1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);

        Optional<CandidateMove> selected = new HeuristicMoveSelector().select(List.of(second, first));

        assertTrue(selected.isPresent());
        assertEquals("C001", selected.get().id());
    }
}
