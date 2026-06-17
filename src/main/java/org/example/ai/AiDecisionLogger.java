package org.example.ai;

import java.io.PrintStream;

public class AiDecisionLogger {
    private final PrintStream out;

    public AiDecisionLogger(PrintStream out) {
        this.out = out;
    }

    public void log(AiDecision decision) {
        out.println("""
                {"candidateId":"%s","source":"%s","model":"%s","elapsedMillis":%d,"heuristicScore":%s,"move":"%s->%s","reason":"%s","fallbackReason":"%s"}
                """.formatted(
                escape(decision.candidateId()),
                decision.source(),
                escape(decision.model()),
                decision.elapsedMillis(),
                decision.heuristicScore() == null ? "null" : decision.heuristicScore(),
                escape(decision.move().getSource()),
                escape(decision.move().getDestination()),
                escape(decision.reason()),
                escape(decision.fallbackReason())
        ).trim());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
