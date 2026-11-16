import { useEffect, useState } from "react";
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import TranslateScreen from "./src/screens/TranslateScreen";
import HistoryScreen from "./src/screens/HistoryScreen";
import SettingsScreen from "./src/screens/SettingsScreen";
import { loadHistory, loadSettings } from "./src/storage";
import { colors } from "./src/theme";

const TABS = ["Translate", "History", "Settings"];

export default function App() {
  const [tab, setTab] = useState("Translate");
  const [settings, setSettings] = useState(null);
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    (async () => {
      const stored = await loadSettings();
      setSettings(stored);
      setHistory(await loadHistory());
      if (!stored.username) {
        setTab("Settings");
      }
    })();
  }, []);

  if (!settings) {
    return <View style={styles.loading}><ActivityIndicator color={colors.teal} /></View>;
  }

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.root} edges={["top", "bottom"]}>
        <StatusBar style="light" />
        <View style={styles.header}>
          <Image source={require("./assets/icon.png")} style={styles.logo} />
          <Text style={styles.title}>Darija Translator</Text>
        </View>
        <View style={styles.body}>
          {tab === "Translate" && (
            <TranslateScreen key={selected?.at ?? "new"} settings={settings} onHistoryChange={setHistory}
                             initialText={selected?.text ?? ""} />
          )}
          {tab === "History" && (
            <HistoryScreen history={history} onHistoryChange={setHistory}
                           onSelect={(item) => { setSelected(item); setTab("Translate"); }} />
          )}
          {tab === "Settings" && <SettingsScreen settings={settings} onSettingsChange={setSettings} />}
        </View>
        <View style={styles.tabBar}>
          {TABS.map((name) => (
            <Pressable key={name} style={styles.tab} onPress={() => setTab(name)} accessibilityRole="tab"
                       accessibilityState={{ selected: tab === name }}>
              <Text style={[styles.tabText, tab === name && styles.tabTextActive]}>{name}</Text>
            </Pressable>
          ))}
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: "center", justifyContent: "center" },
  root: { flex: 1, backgroundColor: colors.teal },
  header: { flexDirection: "row", alignItems: "center", gap: 10, paddingHorizontal: 16, paddingVertical: 12 },
  logo: { width: 30, height: 30, borderRadius: 7 },
  title: { color: colors.white, fontSize: 20, fontWeight: "700" },
  body: { flex: 1, backgroundColor: colors.background },
  tabBar: { flexDirection: "row", backgroundColor: colors.white, borderTopColor: colors.line, borderTopWidth: 1 },
  tab: { flex: 1, alignItems: "center", paddingVertical: 14 },
  tabText: { color: colors.muted, fontSize: 15 },
  tabTextActive: { color: colors.teal, fontWeight: "700" }
});
