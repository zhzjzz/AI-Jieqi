package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Piece;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiAgentTest {
    @Test
    void usesDeepSeekSelectionWhenValid() {
        AiConfig config = new AiConfig("https://api.deepseek.com", "deepseek-v4-flash", "key", 8000, 40);
        DeepSeekClient fakeClient = (prompt, candidates, cfg) -> Optional.of(new DeepSeekSelection(candidates.get(0).id(), 0.9, "first"));
        AiAgent agent = new AiAgent(config, fakeClient);

        AiDecision decision = agent.chooseMove(new GameBoard(7L), Piece.Side.RED, false);

        assertEquals(AiDecisionSource.DEEPSEEK, decision.source());
        assertNotNull(decision.move());
        assertEquals("deepseek-v4-flash", decision.model());
    }

    @Test
    void fallsBackWhenDeepSeekReturnsEmpty() {
        AiConfig config = new AiConfig("https://api.deepseek.com", "deepseek-v4-flash", "key", 8000, 40);
        DeepSeekClient fakeClient = (prompt, candidates, cfg) -> Optional.empty();
        AiAgent agent = new AiAgent(config, fakeClient);

        AiDecision decision = agent.chooseMove(new GameBoard(7L), Piece.Side.RED, false);

        assertEquals(AiDecisionSource.HEURISTIC_FALLBACK, decision.source());
        assertEquals("deepseek_empty_response", decision.fallbackReason());
        assertNotNull(decision.move());
    }

    @Test
    void fallsBackWhenApiKeyIsMissing() {
        AiConfig config = new AiConfig("https://api.deepseek.com", "deepseek-v4-flash", "", 8000, 40);
        DeepSeekClient fakeClient = (prompt, candidates, cfg) -> {
            throw new AssertionError("DeepSeek must not be called without an API key");
        };
        AiAgent agent = new AiAgent(config, fakeClient);

        AiDecision decision = agent.chooseMove(new GameBoard(7L), Piece.Side.RED, false);

        assertEquals(AiDecisionSource.HEURISTIC_FALLBACK, decision.source());
        assertEquals("missing_api_key", decision.fallbackReason());
    }
}
