package ma.aui.translator.llm;

public interface TranslationService {

    TranslationResult translate(String text, String sourceLanguage, Script script);
}
