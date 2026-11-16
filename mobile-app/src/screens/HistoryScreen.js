import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { clearHistory } from "../storage";
import { colors, common } from "../theme";

export default function HistoryScreen({ history, onHistoryChange, onSelect }) {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>{history.length} translation{history.length === 1 ? "" : "s"}</Text>
        {history.length > 0 && (
          <Pressable onPress={async () => onHistoryChange(await clearHistory())} accessibilityRole="button">
            <Text style={styles.clear}>Clear</Text>
          </Pressable>
        )}
      </View>
      <FlatList
        data={history}
        keyExtractor={(item) => String(item.at)}
        ListEmptyComponent={<Text style={common.label}>Your translations will appear here.</Text>}
        renderItem={({ item }) => (
          <Pressable style={common.card} onPress={() => onSelect(item)}>
            <Text style={common.label} numberOfLines={2}>{item.text}</Text>
            <Text style={[styles.translation, { textAlign: item.script === "latin" ? "left" : "right" }]}>
              {item.translation}
            </Text>
          </Pressable>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 14 },
  header: { flexDirection: "row", justifyContent: "space-between", marginBottom: 10 },
  title: { color: colors.ink, fontWeight: "600" },
  clear: { color: colors.teal, fontWeight: "600" },
  translation: { fontSize: 18, color: colors.ink }
});
