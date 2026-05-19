package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.List;
import java.util.Optional;

public class AiAgent {
    private final AiConfig config;
    private final DeepSeekClient deepSeekClient;
    private final RuleEngine ruleEngine;
    private final LegalMoveGenerator moveGenerator;
    private final HeuristicMoveSelector heuristicSelector;
    private final BoardSnapshotFormatter formatter;

    public AiAgent(AiConfig config, DeepSeekClient deepSeekClient) {
        this.config = config;
        this.deepSeekClient = deepSeekClient;
        this.ruleEngine = new RuleEngine();
        this.moveGenerator = new LegalMoveGenerator(ruleEngine);
        this.heuristicSelector = new HeuristicMoveSelector();
        this.formatter = new BoardSnapshotFormatter();
    }

    public AiDecision chooseMove(GameBoard board, Piece.Side side, boolean inCheck) {
        long start = System.currentTimeMillis();
        List<CandidateMove> generated = moveGenerator.generate(board, side);
        List<CandidateMove> scored = heuristicSelector.score(generated).stream()
                .limit(config.maxCandidates())
                .toList();
        CandidateMove fallback = heuristicSelector.select(scored)
                .orElseThrow(() -> new IllegalStateException("No legal moves available for " + side));
        if (!config.hasApiKey()) {
            return fallbackDecision(fallback, start, "missing_api_key");
        }
        try {
            String prompt = formatter.format(board, side, scored, inCheck);
            Optional<DeepSeekSelection> selection = deepSeekClient.select(prompt, scored, config);
            if (selection.isEmpty()) {
                return fallbackDecision(fallback, start, "deepseek_empty_response");
            }
            CandidateMove chosen = scored.stream()
                    .filter(candidate -> candidate.id().equals(selection.get().candidateId()))
                    .findFirst()
                    .orElse(fallback);
            if (!ruleEngine.isLegalMove(board, chosen.move(), side)) {
                return fallbackDecision(fallback, start, "deepseek_illegal_after_validation");
            }
            return new AiDecision(
                    chosen.move(),
                    chosen.id(),
                    AiDecisionSource.DEEPSEEK,
                    config.model(),
                    System.currentTimeMillis() - start,
                    chosen.heuristicScore(),
                    selection.get().confidence(),
                    selection.get().reason(),
                    null
            );
        } catch (Exception e) {
            return fallbackDecision(fallback, start, e.getClass().getSimpleName());
        }
    }

    private AiDecision fallbackDecision(CandidateMove fallback, long start, String reason) {
        Move move = fallback.move();
        return new AiDecision(
                move,
                fallback.id(),
                AiDecisionSource.HEURISTIC_FALLBACK,
                config.model(),
                System.currentTimeMillis() - start,
                fallback.heuristicScore(),
                null,
                "heuristic fallback",
                reason
        );
    }
}
