package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Piece;
import org.example.common.RuleEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalMoveGeneratorTest {
    @Test
    void generatesOnlyLegalMovesWithStableIds() {
        GameBoard board = new GameBoard(7L);
        RuleEngine ruleEngine = new RuleEngine();
        LegalMoveGenerator generator = new LegalMoveGenerator(ruleEngine);

        List<CandidateMove> candidates = generator.generate(board, Piece.Side.RED);

        assertFalse(candidates.isEmpty());
        assertEquals("C001", candidates.get(0).id());
        for (CandidateMove candidate : candidates) {
            assertTrue(ruleEngine.isLegalMove(board, candidate.move(), Piece.Side.RED), candidate.id());
        }
    }

    @Test
    void includesRevealCandidateForOwnHiddenPiece() {
        GameBoard board = new GameBoard(7L);
        LegalMoveGenerator generator = new LegalMoveGenerator(new RuleEngine());

        List<CandidateMove> candidates = generator.generate(board, Piece.Side.RED);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.kind() == MoveKind.REVEAL
                        && candidate.move().getSource().equals(candidate.move().getDestination())));
    }

    @Test
    void classifiesCaptureGeneralCandidate() {
        GameBoard board = new GameBoard(1L);
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                board.set(row, col, null);
            }
        }
        board.set(9, 4, new Piece(Piece.Side.RED, 0, true));
        board.set(1, 4, new Piece(Piece.Side.RED, 1, true));
        board.set(0, 4, new Piece(Piece.Side.BLACK, 0, true));

        List<CandidateMove> candidates = new LegalMoveGenerator(new RuleEngine()).generate(board, Piece.Side.RED);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.kind() == MoveKind.CAPTURE_GENERAL
                        && candidate.move().getSource().equals("e1")
                        && candidate.move().getDestination().equals("e0")));
    }
}
