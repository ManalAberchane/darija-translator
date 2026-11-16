import { StyleSheet } from "react-native";

export const colors = {
  teal: "#0f625c",
  terracotta: "#d67840",
  ink: "#1d2a29",
  muted: "#687776",
  line: "#dfe6e5",
  background: "#f4f7f6",
  error: "#b3261e",
  white: "#ffffff"
};

export const common = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 14,
    padding: 14,
    marginBottom: 12
  },
  label: { color: colors.muted, fontSize: 13, marginBottom: 6 },
  input: {
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: colors.ink,
    backgroundColor: colors.white
  },
  primaryButton: {
    backgroundColor: colors.teal,
    borderRadius: 10,
    paddingVertical: 13,
    alignItems: "center"
  },
  primaryButtonText: { color: colors.white, fontWeight: "700", fontSize: 16 },
  secondaryButton: {
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 10,
    paddingVertical: 9,
    paddingHorizontal: 14,
    alignItems: "center",
    backgroundColor: colors.white
  },
  secondaryButtonText: { color: colors.ink, fontSize: 14 },
  error: { color: colors.error, marginTop: 8 },
  ok: { color: colors.teal, marginTop: 8 }
});
