package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AiAgent {
    private final AiConfig config;
    private final DeepSeekClient deepSeekClient;
    private final RuleEngine ruleEngine;
    private final LegalMoveGenerator moveGenerator;
    private final HeuristicMoveSelector heuristicSelector;
    private final MoveSearchEngine searchEngine;
    private final BoardSnapshotFormatter formatter;

    public AiAgent(AiConfig config, DeepSeekClient deepSeekClient) {
        this.config = config;
        this.deepSeekClient = deepSeekClient;
        this.ruleEngine = new RuleEngine();
        this.moveGenerator = new LegalMoveGenerator(ruleEngine);
        this.heuristicSelector = new HeuristicMoveSelector(ruleEngine);
        this.searchEngine = new MoveSearchEngine(ruleEngine, moveGenerator, heuristicSelector, new PositionEvaluator(ruleEngine));
        this.formatter = new BoardSnapshotFormatter();
    }

    public AiDecision chooseMove(GameBoard board, Piece.Side side, boolean inCheck) {
        long start = System.currentTimeMillis();
        List<SearchAnalysis> ranked = searchEngine.rank(board, side);
        SearchAnalysis fallback = ranked.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal moves available for " + side));
        List<CandidateMove> promptCandidates = ranked.stream()
                .limit(config.maxCandidates())
                .map(SearchAnalysis::candidate)
                .toList();
        if (!config.hasApiKey()) {
            return fallbackDecision(fallback.candidate(), start, "missing_api_key");
        }
        try {
            String prompt = formatter.formatWithSearch(board, side, ranked, inCheck);
            Optional<DeepSeekSelection> selection = deepSeekClient.select(prompt, promptCandidates, config);
            if (selection.isEmpty()) {
                return fallbackDecision(fallback.candidate(), start, "deepseek_empty_response");
            }
            CandidateMove chosen = promptCandidates.stream()
                    .filter(candidate -> candidate.id().equals(selection.get().candidateId()))
                    .findFirst()
                    .orElse(fallback.candidate());
            if (!ruleEngine.isLegalMove(board, chosen.move(), side)) {
                return fallbackDecision(fallback.candidate(), start, "deepseek_illegal_after_validation");
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
            String reason = e.getClass().getSimpleName();
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                reason = reason + ":" + e.getMessage();
            }
            return fallbackDecision(fallback.candidate(), start, reason);
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
