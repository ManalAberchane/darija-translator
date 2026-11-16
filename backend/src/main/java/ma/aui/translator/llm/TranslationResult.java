package ma.aui.translator.llm;

public record TranslationResult(String translation, String detectedSourceLanguage, String model) {
}
