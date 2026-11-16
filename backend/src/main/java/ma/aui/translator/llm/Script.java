package ma.aui.translator.llm;

import java.util.Locale;

public enum Script {
    ARABIC,
    LATIN;

    public static Script parse(String value) {
        if (value == null || value.isBlank()) {
            return ARABIC;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("script must be 'arabic' or 'latin'");
        }
    }
}
