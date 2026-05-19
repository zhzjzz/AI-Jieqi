package org.example.ai;

import org.example.common.Move;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDecisionLoggerTest {
    @Test
    void logsDecisionFields() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AiDecisionLogger logger = new AiDecisionLogger(new PrintStream(out));
        AiDecision decision = new AiDecision(
                new Move("a6", "a5", null, 123L),
                "C001",
                AiDecisionSource.HEURISTIC_FALLBACK,
                "deepseek-v4-flash",
                12L,
                10,
                null,
                "heuristic fallback",
                "missing_api_key"
        );

        logger.log(decision);

        String line = out.toString();
        assertTrue(line.contains("\"candidateId\":\"C001\""));
        assertTrue(line.contains("\"source\":\"HEURISTIC_FALLBACK\""));
        assertTrue(line.contains("\"move\":\"a6->a5\""));
        assertTrue(line.contains("\"fallbackReason\":\"missing_api_key\""));
    }
}
