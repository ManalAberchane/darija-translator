package ma.aui.translator.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application settings read from environment variables, with an optional
 * {@code .env} file in the working directory as a fallback.
 * Real environment variables always win over the file.
 */
public record AppConfig(
        int port,
        String username,
        String password,
        String geminiApiKey,
        String geminiModel,
        String geminiFallbackModel,
        String geminiBaseUrl) {

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    public static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public static AppConfig load() {
        Map<String, String> values = new HashMap<>(readDotEnv(Path.of(".env")));
        values.putAll(System.getenv());
        return fromMap(values);
    }

    public static AppConfig fromMap(Map<String, String> values) {
        String username = blankToNull(values.get("TRANSLATOR_USERNAME"));
        String password = blankToNull(values.get("TRANSLATOR_PASSWORD"));
        if (username == null || password == null) {
            throw new IllegalStateException(
                    "TRANSLATOR_USERNAME and TRANSLATOR_PASSWORD must be set (environment variables or .env file)");
        }
        return new AppConfig(
                parsePort(values.getOrDefault("SERVER_PORT", "8080")),
                username,
                password,
                blankToNull(values.get("GEMINI_API_KEY")),
                valueOrDefault(values.get("GEMINI_MODEL"), DEFAULT_MODEL),
                blankToNull(values.get("GEMINI_FALLBACK_MODEL")),
                valueOrDefault(values.get("GEMINI_BASE_URL"), DEFAULT_GEMINI_BASE_URL));
    }

    public boolean hasGeminiKey() {
        return geminiApiKey != null && !geminiApiKey.startsWith("paste-your");
    }

    static Map<String, String> readDotEnv(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file.toAbsolutePath(), e);
        }
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int eq = line.indexOf('=');
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key.replace("﻿", ""), value);
        }
        return values;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SERVER_PORT must be a number, got: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String valueOrDefault(String value, String fallback) {
        String cleaned = blankToNull(value);
        return cleaned == null ? fallback : cleaned;
    }
}
