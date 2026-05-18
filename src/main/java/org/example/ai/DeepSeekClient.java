package org.example.ai;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface DeepSeekClient {
    Optional<DeepSeekSelection> select(String prompt, List<CandidateMove> candidates, AiConfig config)
            throws IOException, InterruptedException;
}
