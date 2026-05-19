package org.example.ai;

import java.util.Map;
import java.util.stream.Collectors;

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
                .collect(Collectors.toMap(
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
