package org.example.ai;

import org.example.common.Move;

public record AiDecision(
        Move move,
        String candidateId,
        AiDecisionSource source,
        String model,
        long elapsedMillis,
        Integer heuristicScore,
        Double confidence,
        String reason,
        String fallbackReason
) {
}
