package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Piece;

import java.util.List;

public class BoardSnapshotFormatter {
    public String format(GameBoard board, Piece.Side side, List<CandidateMove> candidates, boolean inCheck) {
        StringBuilder builder = new StringBuilder();
        builder.append("side=").append(side).append('\n');
        builder.append("check=").append(inCheck).append('\n');
        builder.append("board:\n");
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                Piece piece = board.get(row, col);
                builder.append(board.coord(row, col)).append('=').append(label(piece)).append(' ');
            }
            builder.append('\n');
        }
        builder.append("candidates:\n");
        for (CandidateMove candidate : candidates) {
            builder.append(candidate.id())
                    .append(' ')
                    .append(candidate.kind())
                    .append(' ')
                    .append(candidate.move().getSource())
                    .append("->")
                    .append(candidate.move().getDestination())
                    .append(" score=")
                    .append(candidate.heuristicScore())
                    .append(" piece=")
                    .append(candidate.pieceLabel())
                    .append(" target=")
                    .append(candidate.targetLabel())
                    .append('\n');
        }
        return builder.toString();
    }

    private String label(Piece piece) {
        if (piece == null) {
            return ".";
        }
        String side = piece.getSide() == Piece.Side.RED ? "R" : "B";
        return side + (piece.isRevealed() ? piece.shortName() : "?");
    }
}
