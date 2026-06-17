package org.example.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpDeepSeekClient implements DeepSeekClient {
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(config.timeoutMillis()))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, config)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("DeepSeek HTTP " + response.statusCode() + ": " + response.body());
        }
        return parser.parse(extractContent(response.body()), candidates);
    }

    private String requestBody(String prompt, AiConfig config) {
        String system = "You are a move selector for Chinese dark chess. Choose exactly one candidateId from the candidate list. Output only a valid JSON object with keys candidateId, confidence, and reason. No markdown, no code fences, no extra text.";
        return """
                {"model":"%s","stream":false,"temperature":0,"max_tokens":256,"thinking":{"type":"disabled"},"response_format":{"type":"json_object"},"messages":[{"role":"system","content":"%s"},{"role":"user","content":"%s"}]}
                """.formatted(escape(config.model()), escape(system), escape(prompt)).trim();
    }

    private String extractContent(String responseBody) {
        Matcher matcher = CONTENT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return responseBody;
        }
        return unescapeJsonString(matcher.group(1));
    }

    private String unescapeJsonString(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                builder.append(switch (ch) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    default -> ch;
                });
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
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
