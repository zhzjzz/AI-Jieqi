# DeepSeek AI Opponent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a headless DeepSeek-powered AI client that joins the existing Jieqi socket server, chooses only locally legal candidate moves, and falls back to deterministic local heuristics when the API cannot provide a valid choice.

**Architecture:** Keep `GameServer`, `GameBoard`, `Move`, `Piece`, and `RuleEngine` as the authority for state and legality. Add a focused `org.example.ai` package for candidate generation, move selection, DeepSeek response parsing, HTTP calling, decision logging, and the headless socket client. All API-dependent behavior is hidden behind interfaces so tests run offline with fake clients.

**Tech Stack:** Java 17, Maven, JavaFX already present, Java built-in `java.net.http.HttpClient`, JUnit 5, Maven Surefire.

---

## File Structure

- Modify `AI-Jieqi-master/pom.xml`: add JUnit 5 and Surefire so AI logic can be tested without GUI or real network calls.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiConfig.java`: reads model, base URL, timeout, max candidates, and API key from env/system properties/defaults.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecision.java`: immutable final decision result used by agent, client, and logger.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionSource.java`: enum for `DEEPSEEK` and `HEURISTIC_FALLBACK`.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java`: wraps candidate ID, `Move`, kind, visible labels, and heuristic score.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/MoveKind.java`: enum for `MOVE`, `CAPTURE`, `REVEAL`, `CAPTURE_GENERAL`.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/LegalMoveGenerator.java`: enumerates legal moves with stable candidate IDs.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/HeuristicMoveSelector.java`: deterministic local fallback selector.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekSelection.java`: parsed model choice.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekResponseParser.java`: strict parser for the required JSON object.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekClient.java`: interface for model selection calls.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/HttpDeepSeekClient.java`: real DeepSeek Chat Completions HTTP implementation.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/BoardSnapshotFormatter.java`: converts board plus candidates into compact prompt text.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiAgent.java`: orchestrates candidates, heuristic ranking, DeepSeek call, validation, and fallback.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionLogger.java`: writes one JSON-like line per AI turn to stdout.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiGameClient.java`: headless socket client with local board state.
- Create `AI-Jieqi-master/src/main/java/org/example/ai/AiClientMain.java`: command-line entry point.
- Modify `AI-Jieqi-master/README.md`: add AI client startup commands and environment variables.
- Create tests under `AI-Jieqi-master/src/test/java/org/example/ai/*Test.java`.

## Shared Commands

Run all test commands from:

```powershell
cd "C:\Users\stay g\Documents\New project 2\AI-Jieqi-master"
```

Use:

```powershell
mvn test
```

Expected final result:

```text
BUILD SUCCESS
```

---

### Task 1: Add Test Infrastructure

**Files:**
- Modify: `AI-Jieqi-master/pom.xml`
- Create: `AI-Jieqi-master/src/test/java/org/example/ai/AiTestSanityTest.java`

- [ ] **Step 1: Write a failing JUnit sanity test**

Create `AI-Jieqi-master/src/test/java/org/example/ai/AiTestSanityTest.java`:

```java
package org.example.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTestSanityTest {
    @Test
    void junitRuns() {
        assertTrue(true);
    }
}
```

- [ ] **Step 2: Run the test to verify infrastructure is missing**

Run:

```powershell
mvn test -Dtest=AiTestSanityTest
```

Expected: FAIL because `org.junit.jupiter.api` is not found or no JUnit 5 test engine is configured.

- [ ] **Step 3: Add JUnit 5 and Surefire**

Modify `AI-Jieqi-master/pom.xml`.

Add this property inside `<properties>`:

```xml
<junit.jupiter.version>5.10.2</junit.jupiter.version>
```

Add this dependency inside `<dependencies>` after the JavaFX dependency:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${junit.jupiter.version}</version>
    <scope>test</scope>
</dependency>
```

Add this plugin inside `<plugins>` after `maven-compiler-plugin`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

- [ ] **Step 4: Run the sanity test**

Run:

```powershell
mvn test -Dtest=AiTestSanityTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add AI-Jieqi-master/pom.xml AI-Jieqi-master/src/test/java/org/example/ai/AiTestSanityTest.java
git commit -m "test: add junit infrastructure"
```

---

### Task 2: Define AI Domain Types and Configuration

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiConfig.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiDecision.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionSource.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/MoveKind.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/AiConfigTest.java`

- [ ] **Step 1: Write the failing config test**

Create `AI-Jieqi-master/src/test/java/org/example/ai/AiConfigTest.java`:

```java
package org.example.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigTest {
    @Test
    void usesDefaultsWhenNoOverridesExist() {
        AiConfig config = AiConfig.from(Map.of(), Map.of());

        assertEquals("https://api.deepseek.com", config.baseUrl());
        assertEquals("deepseek-v4-flash", config.model());
        assertEquals(8000, config.timeoutMillis());
        assertEquals(40, config.maxCandidates());
        assertFalse(config.hasApiKey());
    }

    @Test
    void systemPropertiesOverrideEnvironment() {
        AiConfig config = AiConfig.from(
                Map.of("DEEPSEEK_API_KEY", "env-key", "DEEPSEEK_MODEL", "env-model"),
                Map.of("deepseek.api.key", "prop-key", "deepseek.api.model", "prop-model")
        );

        assertEquals("prop-model", config.model());
        assertEquals("prop-key", config.apiKey());
        assertTrue(config.hasApiKey());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn test -Dtest=AiConfigTest
```

Expected: FAIL because `AiConfig` does not exist.

- [ ] **Step 3: Implement AI domain types**

Create `AI-Jieqi-master/src/main/java/org/example/ai/MoveKind.java`:

```java
package org.example.ai;

public enum MoveKind {
    MOVE,
    CAPTURE,
    REVEAL,
    CAPTURE_GENERAL
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionSource.java`:

```java
package org.example.ai;

public enum AiDecisionSource {
    DEEPSEEK,
    HEURISTIC_FALLBACK
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java`:

```java
package org.example.ai;

import org.example.common.Move;

public record CandidateMove(
        String id,
        Move move,
        MoveKind kind,
        String pieceLabel,
        String targetLabel,
        int heuristicScore
) {
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecision.java`:

```java
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
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiConfig.java`:

```java
package org.example.ai;

import java.util.Map;

public record AiConfig(
        String baseUrl,
        String model,
        String apiKey,
        int timeoutMillis,
        int maxCandidates
) {
    public static AiConfig load() {
        return from(System.getenv(), systemProperties());
    }

    static AiConfig from(Map<String, String> env, Map<String, String> props) {
        String baseUrl = value(props, env, "deepseek.api.baseUrl", "DEEPSEEK_BASE_URL", "https://api.deepseek.com");
        String model = value(props, env, "deepseek.api.model", "DEEPSEEK_MODEL", "deepseek-v4-flash");
        String apiKey = value(props, env, "deepseek.api.key", "DEEPSEEK_API_KEY", "");
        int timeoutMillis = intValue(props, env, "deepseek.api.timeoutMillis", "DEEPSEEK_TIMEOUT_MILLIS", 8000);
        int maxCandidates = intValue(props, env, "deepseek.ai.maxCandidates", "AI_MAX_CANDIDATES", 40);
        return new AiConfig(baseUrl, model, apiKey, timeoutMillis, maxCandidates);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static Map<String, String> systemProperties() {
        return System.getProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue())
                ));
    }

    private static String value(Map<String, String> props, Map<String, String> env,
                                String propKey, String envKey, String defaultValue) {
        String propValue = props.get(propKey);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        String envValue = env.get(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private static int intValue(Map<String, String> props, Map<String, String> env,
                                String propKey, String envKey, int defaultValue) {
        String raw = value(props, env, propKey, envKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
```

- [ ] **Step 4: Run the config test**

Run:

```powershell
mvn test -Dtest=AiConfigTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai AI-Jieqi-master/src/test/java/org/example/ai/AiConfigTest.java
git commit -m "feat: add ai configuration and domain types"
```

---

### Task 3: Generate Stable Legal Move Candidates

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/LegalMoveGenerator.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/LegalMoveGeneratorTest.java`

- [ ] **Step 1: Write the failing generator tests**

Create `AI-Jieqi-master/src/test/java/org/example/ai/LegalMoveGeneratorTest.java`:

```java
package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
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
```

- [ ] **Step 2: Run the generator tests to verify failure**

Run:

```powershell
mvn test -Dtest=LegalMoveGeneratorTest
```

Expected: FAIL because `LegalMoveGenerator` does not exist.

- [ ] **Step 3: Implement `LegalMoveGenerator`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/LegalMoveGenerator.java`:

```java
package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.example.common.RuleEngine;

import java.util.ArrayList;
import java.util.List;

public class LegalMoveGenerator {
    private final RuleEngine ruleEngine;

    public LegalMoveGenerator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public List<CandidateMove> generate(GameBoard board, Piece.Side side) {
        List<CandidateMove> candidates = new ArrayList<>();
        int sequence = 1;
        for (int sr = 0; sr < GameBoard.ROWS; sr++) {
            for (int sc = 0; sc < GameBoard.COLS; sc++) {
                Piece source = board.get(sr, sc);
                if (source == null || source.getSide() != side) {
                    continue;
                }
                if (!source.isRevealed()) {
                    Move reveal = new Move(board.coord(sr, sc), board.coord(sr, sc), null, 0L);
                    if (ruleEngine.isLegalMove(board, reveal, side)) {
                        candidates.add(candidate(board, reveal, MoveKind.REVEAL, sequence++));
                    }
                }
                for (int dr = 0; dr < GameBoard.ROWS; dr++) {
                    for (int dc = 0; dc < GameBoard.COLS; dc++) {
                        if (sr == dr && sc == dc) {
                            continue;
                        }
                        Move move = new Move(board.coord(sr, sc), board.coord(dr, dc), null, 0L);
                        if (ruleEngine.isLegalMove(board, move, side)) {
                            candidates.add(candidate(board, move, classify(board, dr, dc), sequence++));
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private CandidateMove candidate(GameBoard board, Move move, MoveKind kind, int sequence) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        Piece target = board.get(dr, dc);
        return new CandidateMove(
                "C" + String.format("%03d", sequence),
                move,
                kind,
                label(source),
                target == null ? "EMPTY" : label(target),
                0
        );
    }

    private MoveKind classify(GameBoard board, int row, int col) {
        Piece target = board.get(row, col);
        if (target == null) {
            return MoveKind.MOVE;
        }
        return target.getType() == 0 ? MoveKind.CAPTURE_GENERAL : MoveKind.CAPTURE;
    }

    private String label(Piece piece) {
        if (piece == null) {
            return "EMPTY";
        }
        String side = piece.getSide() == Piece.Side.RED ? "RED" : "BLACK";
        return side + "_" + (piece.isRevealed() ? piece.shortName() : "HIDDEN");
    }
}
```

- [ ] **Step 4: Run the generator tests**

Run:

```powershell
mvn test -Dtest=LegalMoveGeneratorTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/LegalMoveGenerator.java AI-Jieqi-master/src/test/java/org/example/ai/LegalMoveGeneratorTest.java
git commit -m "feat: generate legal ai move candidates"
```

---

### Task 4: Add Deterministic Heuristic Fallback

**Files:**
- Modify: `AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/HeuristicMoveSelector.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/HeuristicMoveSelectorTest.java`

- [ ] **Step 1: Write failing heuristic tests**

Create `AI-Jieqi-master/src/test/java/org/example/ai/HeuristicMoveSelectorTest.java`:

```java
package org.example.ai;

import org.example.common.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicMoveSelectorTest {
    @Test
    void prefersCapturingGeneral() {
        CandidateMove quiet = new CandidateMove("C001", new Move("a0", "a1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);
        CandidateMove win = new CandidateMove("C002", new Move("e1", "e0", null, 0L), MoveKind.CAPTURE_GENERAL, "RED_车", "BLACK_将", 0);

        Optional<CandidateMove> selected = new HeuristicMoveSelector().select(List.of(quiet, win));

        assertTrue(selected.isPresent());
        assertEquals("C002", selected.get().id());
        assertTrue(selected.get().heuristicScore() > quiet.heuristicScore());
    }

    @Test
    void usesStableIdOrderWhenScoresTie() {
        CandidateMove second = new CandidateMove("C002", new Move("b0", "b1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);
        CandidateMove first = new CandidateMove("C001", new Move("a0", "a1", null, 0L), MoveKind.MOVE, "RED_车", "EMPTY", 0);

        Optional<CandidateMove> selected = new HeuristicMoveSelector().select(List.of(second, first));

        assertTrue(selected.isPresent());
        assertEquals("C001", selected.get().id());
    }
}
```

- [ ] **Step 2: Run heuristic tests to verify failure**

Run:

```powershell
mvn test -Dtest=HeuristicMoveSelectorTest
```

Expected: FAIL because `HeuristicMoveSelector` does not exist or candidate scores are not recalculated.

- [ ] **Step 3: Add a score-copy helper to `CandidateMove`**

Modify `AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java`:

```java
package org.example.ai;

import org.example.common.Move;

public record CandidateMove(
        String id,
        Move move,
        MoveKind kind,
        String pieceLabel,
        String targetLabel,
        int heuristicScore
) {
    public CandidateMove withHeuristicScore(int score) {
        return new CandidateMove(id, move, kind, pieceLabel, targetLabel, score);
    }
}
```

- [ ] **Step 4: Implement `HeuristicMoveSelector`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/HeuristicMoveSelector.java`:

```java
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
        int score = switch (candidate.kind()) {
            case CAPTURE_GENERAL -> 100000;
            case CAPTURE -> 500 + capturedValue(candidate.targetLabel());
            case REVEAL -> hasCapture ? 40 : 80;
            case MOVE -> 10;
        };
        return score;
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
```

- [ ] **Step 5: Run heuristic tests**

Run:

```powershell
mvn test -Dtest=HeuristicMoveSelectorTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/CandidateMove.java AI-Jieqi-master/src/main/java/org/example/ai/HeuristicMoveSelector.java AI-Jieqi-master/src/test/java/org/example/ai/HeuristicMoveSelectorTest.java
git commit -m "feat: add heuristic ai fallback selector"
```

---

### Task 5: Parse DeepSeek Candidate Choices Strictly

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekSelection.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekResponseParser.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/DeepSeekResponseParserTest.java`

- [ ] **Step 1: Write failing parser tests**

Create `AI-Jieqi-master/src/test/java/org/example/ai/DeepSeekResponseParserTest.java`:

```java
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
```

- [ ] **Step 2: Run parser tests to verify failure**

Run:

```powershell
mvn test -Dtest=DeepSeekResponseParserTest
```

Expected: FAIL because parser classes do not exist.

- [ ] **Step 3: Implement parser classes**

Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekSelection.java`:

```java
package org.example.ai;

public record DeepSeekSelection(String candidateId, Double confidence, String reason) {
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekResponseParser.java`:

```java
package org.example.ai;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeepSeekResponseParser {
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern CONFIDENCE_FIELD = Pattern.compile("\"confidence\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    public Optional<DeepSeekSelection> parse(String body, List<CandidateMove> candidates) {
        if (body == null) {
            return Optional.empty();
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return Optional.empty();
        }
        String candidateId = stringField(trimmed, "candidateId");
        if (candidateId == null || candidates.stream().noneMatch(candidate -> candidate.id().equals(candidateId))) {
            return Optional.empty();
        }
        Double confidence = confidence(trimmed);
        String reason = stringField(trimmed, "reason");
        if (reason != null && reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        return Optional.of(new DeepSeekSelection(candidateId, confidence, reason));
    }

    private String stringField(String body, String name) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(name))).matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Double confidence(String body) {
        Matcher matcher = CONFIDENCE_FIELD.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run parser tests**

Run:

```powershell
mvn test -Dtest=DeepSeekResponseParserTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekSelection.java AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekResponseParser.java AI-Jieqi-master/src/test/java/org/example/ai/DeepSeekResponseParserTest.java
git commit -m "feat: parse deepseek move selections"
```

---

### Task 6: Build Prompt Formatting and DeepSeek HTTP Client

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/BoardSnapshotFormatter.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekClient.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/HttpDeepSeekClient.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/BoardSnapshotFormatterTest.java`

- [ ] **Step 1: Write failing formatter test**

Create `AI-Jieqi-master/src/test/java/org/example/ai/BoardSnapshotFormatterTest.java`:

```java
package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSnapshotFormatterTest {
    @Test
    void includesSideBoardAndCandidateIds() {
        GameBoard board = new GameBoard(7L);
        List<CandidateMove> candidates = List.of(
                new CandidateMove("C001", new Move("a6", "a5", null, 0L), MoveKind.MOVE, "RED_HIDDEN", "EMPTY", 10)
        );

        String prompt = new BoardSnapshotFormatter().format(board, Piece.Side.RED, candidates, false);

        assertTrue(prompt.contains("side=RED"));
        assertTrue(prompt.contains("check=false"));
        assertTrue(prompt.contains("C001"));
        assertTrue(prompt.contains("a6"));
    }
}
```

- [ ] **Step 2: Run formatter test to verify failure**

Run:

```powershell
mvn test -Dtest=BoardSnapshotFormatterTest
```

Expected: FAIL because formatter does not exist.

- [ ] **Step 3: Implement formatter and DeepSeek client interface**

Create `AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekClient.java`:

```java
package org.example.ai;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface DeepSeekClient {
    Optional<DeepSeekSelection> select(String prompt, List<CandidateMove> candidates, AiConfig config)
            throws IOException, InterruptedException;
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/BoardSnapshotFormatter.java`:

```java
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
```

- [ ] **Step 4: Implement `HttpDeepSeekClient`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/HttpDeepSeekClient.java`:

```java
package org.example.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class HttpDeepSeekClient implements DeepSeekClient {
    private final HttpClient httpClient;
    private final DeepSeekResponseParser parser;

    public HttpDeepSeekClient() {
        this(HttpClient.newHttpClient(), new DeepSeekResponseParser());
    }

    HttpDeepSeekClient(HttpClient httpClient, DeepSeekResponseParser parser) {
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public Optional<DeepSeekSelection> select(String prompt, List<CandidateMove> candidates, AiConfig config)
            throws IOException, InterruptedException {
        if (!config.hasApiKey()) {
            return Optional.empty();
        }
        String requestBody = requestBody(prompt, config);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(config.timeoutMillis()))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        String content = extractContent(response.body());
        return parser.parse(content, candidates);
    }

    private String requestBody(String prompt, AiConfig config) {
        String system = "你是揭棋 AI 的候选走法选择器。你不能发明走法，只能从候选列表中选择一个 id。返回必须是 JSON 对象。";
        return """
                {"model":"%s","stream":false,"messages":[{"role":"system","content":"%s"},{"role":"user","content":"%s"}]}
                """.formatted(escape(config.model()), escape(system), escape(prompt)).trim();
    }

    private String extractContent(String responseBody) {
        String marker = "\"content\":\"";
        int start = responseBody.indexOf(marker);
        if (start < 0) {
            return responseBody;
        }
        start += marker.length();
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < responseBody.length(); i++) {
            char ch = responseBody.charAt(i);
            if (escaped) {
                builder.append(switch (ch) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> ch;
                });
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
```

- [ ] **Step 5: Run formatter test and compile**

Run:

```powershell
mvn test -Dtest=BoardSnapshotFormatterTest
```

Expected: PASS.

Run:

```powershell
mvn test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/BoardSnapshotFormatter.java AI-Jieqi-master/src/main/java/org/example/ai/DeepSeekClient.java AI-Jieqi-master/src/main/java/org/example/ai/HttpDeepSeekClient.java AI-Jieqi-master/src/test/java/org/example/ai/BoardSnapshotFormatterTest.java
git commit -m "feat: add deepseek prompt and http client"
```

---

### Task 7: Orchestrate Decisions in `AiAgent`

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiAgent.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/AiAgentTest.java`

- [ ] **Step 1: Write failing agent tests**

Create `AI-Jieqi-master/src/test/java/org/example/ai/AiAgentTest.java`:

```java
package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;
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
```

- [ ] **Step 2: Run agent tests to verify failure**

Run:

```powershell
mvn test -Dtest=AiAgentTest
```

Expected: FAIL because `AiAgent` does not exist.

- [ ] **Step 3: Implement `AiAgent`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiAgent.java`:

```java
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
```

- [ ] **Step 4: Run agent tests**

Run:

```powershell
mvn test -Dtest=AiAgentTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/AiAgent.java AI-Jieqi-master/src/test/java/org/example/ai/AiAgentTest.java
git commit -m "feat: orchestrate ai move decisions"
```

---

### Task 8: Add Decision Logging and Headless Socket Client

**Files:**
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionLogger.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiGameClient.java`
- Create: `AI-Jieqi-master/src/main/java/org/example/ai/AiClientMain.java`
- Test: `AI-Jieqi-master/src/test/java/org/example/ai/AiDecisionLoggerTest.java`

- [ ] **Step 1: Write failing logger test**

Create `AI-Jieqi-master/src/test/java/org/example/ai/AiDecisionLoggerTest.java`:

```java
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
```

- [ ] **Step 2: Run logger test to verify failure**

Run:

```powershell
mvn test -Dtest=AiDecisionLoggerTest
```

Expected: FAIL because `AiDecisionLogger` does not exist.

- [ ] **Step 3: Implement `AiDecisionLogger`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionLogger.java`:

```java
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
```

- [ ] **Step 4: Implement `AiGameClient` and `AiClientMain`**

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiClientMain.java`:

```java
package org.example.ai;

public class AiClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        new AiGameClient(host, port, new AiAgent(AiConfig.load(), new HttpDeepSeekClient()), new AiDecisionLogger(System.out)).start();
    }
}
```

Create `AI-Jieqi-master/src/main/java/org/example/ai/AiGameClient.java`:

```java
package org.example.ai;

import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class AiGameClient {
    private final String host;
    private final int port;
    private final AiAgent agent;
    private final AiDecisionLogger logger;
    private GameBoard board;
    private Piece.Side mySide;
    private boolean inCheck;

    public AiGameClient(String host, int port, AiAgent agent, AiDecisionLogger logger) {
        this.host = host;
        this.port = port;
        this.agent = agent;
        this.logger = logger;
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            while (true) {
                Object obj = input.readObject();
                if (obj instanceof Move move) {
                    applyMove(move);
                    continue;
                }
                String message = String.valueOf(obj);
                if (message.startsWith("COLOR:")) {
                    mySide = "RED".equals(message.substring("COLOR:".length())) ? Piece.Side.RED : Piece.Side.BLACK;
                } else if (message.startsWith("SEED:")) {
                    board = new GameBoard(Long.parseLong(message.substring("SEED:".length())));
                } else if (message.startsWith("CHECK:")) {
                    inCheck = Boolean.parseBoolean(message.substring("CHECK:".length()));
                } else if (message.startsWith("YOUR_TURN:") && Boolean.parseBoolean(message.substring("YOUR_TURN:".length()))) {
                    AiDecision decision = agent.chooseMove(board, mySide, inCheck);
                    decision.move().setTurnStartTime(System.currentTimeMillis());
                    logger.log(decision);
                    output.writeObject(decision.move());
                    output.flush();
                } else if (message.startsWith("WINNER:")) {
                    System.out.println(message);
                    return;
                } else if (message.startsWith("ERROR:")) {
                    System.out.println(message);
                }
            }
        } catch (Exception e) {
            System.out.println("AI client ended: " + e.getMessage());
        }
    }

    private void applyMove(Move move) {
        int sr = board.rowFromCoord(move.getSource());
        int sc = board.colFromCoord(move.getSource());
        int dr = board.rowFromCoord(move.getDestination());
        int dc = board.colFromCoord(move.getDestination());
        Piece source = board.get(sr, sc);
        if (source == null) {
            return;
        }
        if (sr == dr && sc == dc) {
            source.setRevealed(true);
            if (move.getType() != null) {
                source.setType(move.getType());
            }
            return;
        }
        board.set(dr, dc, source);
        board.set(sr, sc, null);
        source.setRevealed(true);
        if (move.getType() != null) {
            source.setType(move.getType());
        }
    }
}
```

- [ ] **Step 5: Run logger test and compile**

Run:

```powershell
mvn test -Dtest=AiDecisionLoggerTest
```

Expected: PASS.

Run:

```powershell
mvn test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add AI-Jieqi-master/src/main/java/org/example/ai/AiDecisionLogger.java AI-Jieqi-master/src/main/java/org/example/ai/AiGameClient.java AI-Jieqi-master/src/main/java/org/example/ai/AiClientMain.java AI-Jieqi-master/src/test/java/org/example/ai/AiDecisionLoggerTest.java
git commit -m "feat: add headless ai socket client"
```

---

### Task 9: Document AI Startup and Run Full Verification

**Files:**
- Modify: `AI-Jieqi-master/README.md`

- [ ] **Step 1: Update README with AI commands**

Append this section to `AI-Jieqi-master/README.md`:

````markdown
## DeepSeek AI 客户端

AI 客户端可以作为一个无 GUI 的 Socket 客户端加入现有服务器。它会先用本地规则引擎生成合法候选走法，再调用 DeepSeek API 选择候选；如果没有配置 API key 或 API 返回不可用结果，会使用本地启发式兜底。

### 环境变量

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
$env:DEEPSEEK_TIMEOUT_MILLIS="8000"
$env:AI_MAX_CANDIDATES="40"
```

不设置 `DEEPSEEK_API_KEY` 时，AI 会自动进入本地启发式模式。

### 启动方式

先启动服务器：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.server.ServerMain
```

再启动真人客户端或另一个 AI 客户端。启动 AI 客户端：

```powershell
mvn -q exec:java -Dexec.mainClass=org.example.ai.AiClientMain -Dexec.args="127.0.0.1 5000"
```

AI 每步会输出一行决策日志，包含候选 ID、走法、来源、耗时和兜底原因。
````

- [ ] **Step 2: Run all tests**

Run:

```powershell
mvn test
```

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 3: Compile AI main class through exec plugin without requiring a server**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: PASS with no compilation errors.

- [ ] **Step 4: Commit**

```powershell
git add AI-Jieqi-master/README.md
git commit -m "docs: document deepseek ai client"
```

---

## Self-Review Checklist

- Spec coverage: tasks cover configuration, legal candidate generation, heuristic fallback, DeepSeek parsing, HTTP client, prompt formatting, AI orchestration, socket client, decision logging, README commands, and offline tests.
- API key safety: `DEEPSEEK_API_KEY` is read from environment/system properties and is not logged.
- Testability: core behavior is tested with fake `DeepSeekClient`; no default test requires real network access.
- Rule authority: `RuleEngine.isLegalMove` remains the gate for generated candidates and DeepSeek selections.
- Scope control: no Alpha-Beta search, GUI redesign, database, or account work is included.
