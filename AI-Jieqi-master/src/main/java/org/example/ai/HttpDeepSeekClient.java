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
