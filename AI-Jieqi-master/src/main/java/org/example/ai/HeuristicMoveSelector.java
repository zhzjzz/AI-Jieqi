package org.example.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HeuristicMoveSelector {
    public List<CandidateMove> score(List<CandidateMove> candidates) {
        boolean hasCapture = candidates.stream()
                .anyMatch(candidate -> candidate.kind() == MoveKind.CAPTURE || candidate.kind() == MoveKind.CAPTURE_GENERAL);
        return candidates.stream()
                .map(candidate -> candidate.withHeuristicScore(score(candidate, hasCapture)))
                .sorted(Comparator.comparingInt(CandidateMove::heuristicScore).reversed()
                        .thenComparing(CandidateMove::id))
                .toList();
    }

    public Optional<CandidateMove> select(List<CandidateMove> candidates) {
        return score(candidates).stream().findFirst();
    }

    private int score(CandidateMove candidate, boolean hasCapture) {
        return switch (candidate.kind()) {
            case CAPTURE_GENERAL -> 100000;
            case CAPTURE -> 500 + capturedValue(candidate.targetLabel());
            case REVEAL -> hasCapture ? 40 : 80;
            case MOVE -> 10;
        };
    }

    private int capturedValue(String targetLabel) {
        if (targetLabel == null || targetLabel.endsWith("HIDDEN")) {
            return 330;
        }
        if (targetLabel.endsWith("将")) {
            return 100000;
        }
        if (targetLabel.endsWith("车")) {
            return 900;
        }
        if (targetLabel.endsWith("炮")) {
            return 450;
        }
        if (targetLabel.endsWith("马")) {
            return 400;
        }
        if (targetLabel.endsWith("象")) {
            return 220;
        }
        if (targetLabel.endsWith("士")) {
            return 200;
        }
        if (targetLabel.endsWith("兵")) {
            return 100;
        }
        return 330;
    }
}
