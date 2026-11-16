package ma.aui.translator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void readsDotEnvWithCommentsQuotesAndBom(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".env");
        Files.writeString(file, "﻿# comment\nGEMINI_API_KEY=\"abc\"\n\nTRANSLATOR_USERNAME = manal \r\n");
        Map<String, String> values = AppConfig.readDotEnv(file);
        assertEquals("abc", values.get("GEMINI_API_KEY"));
        assertEquals("manal", values.get("TRANSLATOR_USERNAME"));
    }

    @Test
    void appliesDefaults() {
        AppConfig config = AppConfig.fromMap(Map.of("TRANSLATOR_USERNAME", "u", "TRANSLATOR_PASSWORD", "p"));
        assertEquals(8080, config.port());
        assertEquals(AppConfig.DEFAULT_MODEL, config.geminiModel());
        assertNull(config.geminiFallbackModel());
        assertFalse(config.hasGeminiKey());
    }

    @Test
    void placeholderKeyCountsAsMissing() {
        AppConfig config = AppConfig.fromMap(Map.of("TRANSLATOR_USERNAME", "u", "TRANSLATOR_PASSWORD", "p",
                "GEMINI_API_KEY", "paste-your-key-here"));
        assertFalse(config.hasGeminiKey());
    }

    @Test
    void credentialsAreRequired() {
        assertThrows(IllegalStateException.class, () -> AppConfig.fromMap(Map.of("TRANSLATOR_USERNAME", "u")));
    }
}
