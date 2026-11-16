package ma.aui.translator.rest;

public record TranslationResponse(
        String translation,
        String sourceLanguage,
        String targetLanguage,
        String script,
        String model) {
}
