import AsyncStorage from "@react-native-async-storage/async-storage";

const SETTINGS_KEY = "darija.settings";
const HISTORY_KEY = "darija.history";
const HISTORY_LIMIT = 30;

export const DEFAULT_SETTINGS = { serverUrl: "http://192.168.1.10:8080", username: "", password: "" };

export async function loadSettings() {
  const raw = await AsyncStorage.getItem(SETTINGS_KEY);
  return { ...DEFAULT_SETTINGS, ...(raw ? JSON.parse(raw) : {}) };
}

export function saveSettings(settings) {
  return AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

export async function loadHistory() {
  const raw = await AsyncStorage.getItem(HISTORY_KEY);
  return raw ? JSON.parse(raw) : [];
}

export async function addToHistory(entry) {
  const history = [{ ...entry, at: Date.now() }, ...(await loadHistory())].slice(0, HISTORY_LIMIT);
  await AsyncStorage.setItem(HISTORY_KEY, JSON.stringify(history));
  return history;
}

export async function clearHistory() {
  await AsyncStorage.setItem(HISTORY_KEY, JSON.stringify([]));
  return [];
}
