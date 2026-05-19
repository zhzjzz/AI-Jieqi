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
