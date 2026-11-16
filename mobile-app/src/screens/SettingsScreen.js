import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { checkConnection } from "../api";
import { saveSettings } from "../storage";
import { colors, common } from "../theme";

export default function SettingsScreen({ settings, onSettingsChange }) {
  const [form, setForm] = useState(settings);
  const [message, setMessage] = useState(null);
  const update = (key) => (value) => setForm({ ...form, [key]: value });

  async function onSave() {
    const cleaned = { ...form, serverUrl: form.serverUrl.trim(), username: form.username.trim() };
    await saveSettings(cleaned);
    onSettingsChange(cleaned);
    setMessage({ kind: "ok", text: "Settings saved" });
  }

  async function onTest() {
    setMessage({ kind: "ok", text: "Checking…" });
    try {
      const text = await checkConnection(form);
      setMessage({ kind: text.startsWith("Connected and") ? "ok" : "error", text });
    } catch (error) {
      setMessage({ kind: "error", text: error.message });
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <View style={common.card}>
        <Text style={common.label}>REST API URL (your laptop's IP address, port 8080)</Text>
        <TextInput style={[common.input, styles.field]} value={form.serverUrl} onChangeText={update("serverUrl")}
                   autoCapitalize="none" autoCorrect={false} keyboardType="url" testID="serverUrl" />
        <Text style={common.label}>Username</Text>
        <TextInput style={[common.input, styles.field]} value={form.username} onChangeText={update("username")}
                   autoCapitalize="none" autoCorrect={false} testID="username" />
        <Text style={common.label}>Password</Text>
        <TextInput style={[common.input, styles.field]} value={form.password} onChangeText={update("password")}
                   secureTextEntry autoCapitalize="none" testID="password" />
        <View style={styles.row}>
          <Pressable style={[common.primaryButton, styles.grow]} onPress={onSave} accessibilityRole="button">
            <Text style={common.primaryButtonText}>Save</Text>
          </Pressable>
          <Pressable style={[common.secondaryButton, styles.grow]} onPress={onTest} accessibilityRole="button">
            <Text style={common.secondaryButtonText}>Test connection</Text>
          </Pressable>
        </View>
        {message && <Text style={message.kind === "error" ? common.error : common.ok}>{message.text}</Text>}
      </View>
      <Text style={styles.hint}>
        The backend prints its network address when it starts, for example http://192.168.1.23:8080.
        The phone and the laptop must be on the same Wi-Fi (or connect the laptop to the phone's hotspot).
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 14 },
  field: { marginBottom: 12 },
  row: { flexDirection: "row", gap: 10, alignItems: "stretch" },
  grow: { flex: 1, justifyContent: "center" },
  hint: { color: colors.muted, fontSize: 13, lineHeight: 19, paddingHorizontal: 4 }
});
