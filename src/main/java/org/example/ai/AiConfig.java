package org.example.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public record AiConfig(
        String baseUrl,
        String model,
        String apiKey,
        int timeoutMillis,
        int maxCandidates
) {
    public static AiConfig load() {
        return from(System.getenv(), mergeProperties(localFileProperties(), systemProperties()));
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

    private static Map<String, String> localFileProperties() {
        Path path = Path.of("ai.local.properties");
        if (!Files.exists(path)) {
            return Map.of();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException e) {
            return Map.of();
        }

        return properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(name -> name, properties::getProperty));
    }

    private static Map<String, String> mergeProperties(Map<String, String> base, Map<String, String> overrides) {
        return java.util.stream.Stream.concat(base.entrySet().stream(), overrides.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (ignored, replacement) -> replacement
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
