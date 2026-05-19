package org.example.ai;

import org.example.common.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekResponseParserTest {
    private final List<CandidateMove> candidates = List.of(
            new CandidateMove("C001", new Move("a0", "a1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 10),
            new CandidateMove("C002", new Move("b0", "b1", null, 0L), MoveKind.REVEAL, "RED_HIDDEN", "RED_HIDDEN", 80)
    );

    @Test
    void parsesKnownCandidateId() {
        Optional<DeepSeekSelection> selection = new DeepSeekResponseParser()
                .parse("{\"candidateId\":\"C002\",\"confidence\":0.75,\"reason\":\"翻开暗子\"}", candidates);

        assertTrue(selection.isPresent());
        assertEquals("C002", selection.get().candidateId());
        assertEquals(0.75, selection.get().confidence());
        assertEquals("翻开暗子", selection.get().reason());
    }

    @Test
    void rejectsUnknownCandidateId() {
        Optional<DeepSeekSelection> selection = new DeepSeekResponseParser()
                .parse("{\"candidateId\":\"C999\",\"confidence\":0.75,\"reason\":\"bad\"}", candidates);

        assertTrue(selection.isEmpty());
    }

    @Test
    void rejectsNonJsonText() {
        Optional<DeepSeekSelection> selection = new DeepSeekResponseParser()
                .parse("I choose C001", candidates);

        assertTrue(selection.isEmpty());
    }
}
