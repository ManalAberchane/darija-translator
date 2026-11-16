import { useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import * as Clipboard from "expo-clipboard";
import * as Speech from "expo-speech";
import { translate } from "../api";
import { addToHistory } from "../storage";
import { colors, common } from "../theme";

const LANGUAGES = ["auto", "English", "French", "Spanish", "German", "Modern Standard Arabic"];

function Chip({ label, selected, onPress }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.chipSelected]} accessibilityRole="button">
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{label}</Text>
    </Pressable>
  );
}

export default function TranslateScreen({ settings, onHistoryChange, initialText = "" }) {
  const [text, setText] = useState(initialText);
  const [sourceLanguage, setSourceLanguage] = useState("auto");
  const [script, setScript] = useState("arabic");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);

  async function onTranslate() {
    if (!text.trim()) {
      setMessage({ kind: "error", text: "Enter some text first" });
      return;
    }
    setLoading(true);
    setMessage(null);
    try {
      const response = await translate(settings, { text: text.trim(), sourceLanguage, script });
      setResult(response);
      onHistoryChange(await addToHistory({
        text: text.trim(),
        translation: response.translation,
        script: response.script,
        source: response.sourceLanguage
      }));
    } catch (error) {
      setMessage({ kind: "error", text: error.message });
    } finally {
      setLoading(false);
    }
  }

  async function onCopy() {
    await Clipboard.setStringAsync(result.translation);
    setMessage({ kind: "ok", text: "Translation copied" });
  }

  function onSpeak() {
    Speech.stop();
    Speech.speak(result.translation, { language: result.script === "latin" ? "fr-FR" : "ar", rate: 0.95 });
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <View style={common.card}>
        <Text style={common.label}>From</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipRow}>
          {LANGUAGES.map((language) => (
            <Chip key={language} label={language === "auto" ? "Detect" : language}
                  selected={sourceLanguage === language} onPress={() => setSourceLanguage(language)} />
          ))}
        </ScrollView>
        <Text style={common.label}>Darija script</Text>
        <View style={styles.chipRowStatic}>
          <Chip label="عربي" selected={script === "arabic"} onPress={() => setScript("arabic")} />
          <Chip label="Latin (Arabizi)" selected={script === "latin"} onPress={() => setScript("latin")} />
        </View>
        <TextInput
          style={[common.input, styles.textArea]}
          multiline
          maxLength={5000}
          value={text}
          onChangeText={setText}
          placeholder="Type or paste text to translate…"
          placeholderTextColor={colors.muted}
          testID="sourceText"
        />
        <Pressable style={[common.primaryButton, loading && styles.disabled]} onPress={onTranslate} disabled={loading}
                   accessibilityRole="button" testID="translateButton">
          {loading ? <ActivityIndicator color={colors.white} />
                   : <Text style={common.primaryButtonText}>Translate to Darija</Text>}
        </Pressable>
        {message && <Text style={message.kind === "error" ? common.error : common.ok}>{message.text}</Text>}
      </View>

      {result && (
        <View style={[common.card, styles.resultCard]}>
          <Text style={common.label}>{result.sourceLanguage || "Detected"} → Darija</Text>
          <Text selectable testID="translation"
                style={[styles.translation, { textAlign: result.script === "latin" ? "left" : "right",
                  writingDirection: result.script === "latin" ? "ltr" : "rtl" }]}>
            {result.translation}
          </Text>
          <View style={styles.actions}>
            <Pressable style={common.secondaryButton} onPress={onSpeak} accessibilityRole="button">
              <Text style={common.secondaryButtonText}>🔊 Listen</Text>
            </Pressable>
            <Pressable style={common.secondaryButton} onPress={onCopy} accessibilityRole="button">
              <Text style={common.secondaryButtonText}>📋 Copy</Text>
            </Pressable>
          </View>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 14 },
  chipRow: { marginBottom: 12, flexGrow: 0 },
  chipRowStatic: { flexDirection: "row", marginBottom: 12 },
  chip: {
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 18,
    paddingVertical: 6,
    paddingHorizontal: 12,
    marginRight: 8,
    backgroundColor: colors.white
  },
  chipSelected: { backgroundColor: colors.teal, borderColor: colors.teal },
  chipText: { color: colors.ink, fontSize: 14 },
  chipTextSelected: { color: colors.white, fontWeight: "600" },
  textArea: { minHeight: 120, textAlignVertical: "top", marginBottom: 12 },
  disabled: { opacity: 0.6 },
  resultCard: { borderTopColor: colors.terracotta, borderTopWidth: 4 },
  translation: { fontSize: 22, lineHeight: 36, color: colors.ink, marginVertical: 6 },
  actions: { flexDirection: "row", gap: 10, marginTop: 6 }
});
