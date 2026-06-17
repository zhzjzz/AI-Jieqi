package org.example.ai;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeepSeekResponseParser {
    private static final Pattern CANDIDATE_ID_FIELD = Pattern.compile("\"candidateId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIDENCE_FIELD = Pattern.compile("\"confidence\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    public Optional<DeepSeekSelection> parse(String body, List<CandidateMove> candidates) {
        if (body == null) {
            return Optional.empty();
        }
        String trimmed = body.trim();
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
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if ("candidateId".equals(name)) {
            Matcher fallback = CANDIDATE_ID_FIELD.matcher(body);
            return fallback.find() ? fallback.group(1) : null;
        }
        return null;
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
