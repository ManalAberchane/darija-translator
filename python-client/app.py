"""Desktop client (Tkinter) for the Darija Translator REST API."""
import threading
import tkinter as tk
from tkinter import ttk

from darija_client import ClientConfig, DarijaTranslatorClient, TranslationError

LANGUAGES = ["auto", "English", "French", "Spanish", "German", "Italian", "Modern Standard Arabic"]
TEAL = "#0f625c"


class TranslatorApp:
    def __init__(self, root: tk.Tk, client: DarijaTranslatorClient):
        self.root = root
        self.client = client
        self.history: list[tuple[str, str]] = []
        root.title("Darija Translator - Python client")
        root.geometry("620x620")
        root.minsize(480, 480)

        frame = ttk.Frame(root, padding=14)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="Darija Translator", font=("Segoe UI", 16, "bold"), foreground=TEAL).pack(anchor="w")
        ttk.Label(frame, text=f"API: {client.config.api_url}", foreground="#687776").pack(anchor="w", pady=(0, 8))

        options = ttk.Frame(frame)
        options.pack(fill="x", pady=(0, 6))
        ttk.Label(options, text="From").pack(side="left")
        self.source = ttk.Combobox(options, values=LANGUAGES, state="readonly", width=24)
        self.source.set("auto")
        self.source.pack(side="left", padx=(6, 16))
        self.script = tk.StringVar(value="arabic")
        ttk.Radiobutton(options, text="Arabic letters", variable=self.script, value="arabic").pack(side="left")
        ttk.Radiobutton(options, text="Latin (Arabizi)", variable=self.script, value="latin").pack(side="left", padx=8)

        self.input = tk.Text(frame, height=6, wrap="word", font=("Segoe UI", 12), relief="solid", borderwidth=1)
        self.input.pack(fill="x")
        self.input.bind("<Control-Return>", lambda _: self.translate() or "break")

        buttons = ttk.Frame(frame)
        buttons.pack(fill="x", pady=8)
        self.translate_button = ttk.Button(buttons, text="Translate to Darija", command=self.translate)
        self.translate_button.pack(side="left")
        ttk.Button(buttons, text="Copy translation", command=self.copy).pack(side="left", padx=8)
        self.status = ttk.Label(buttons, text="", foreground="#687776")
        self.status.pack(side="left", padx=8)

        self.output = tk.Text(frame, height=5, wrap="word", font=("Segoe UI", 16), relief="solid", borderwidth=1,
                              background="#f4f7f6", state="disabled")
        self.output.tag_configure("rtl", justify="right")
        self.output.pack(fill="x")

        ttk.Label(frame, text="History", font=("Segoe UI", 11, "bold")).pack(anchor="w", pady=(12, 2))
        self.history_list = tk.Listbox(frame, height=8, font=("Segoe UI", 11), activestyle="none")
        self.history_list.pack(fill="both", expand=True)

    def translate(self) -> None:
        text = self.input.get("1.0", "end").strip()
        if not text:
            self.set_status("Enter some text first", error=True)
            return
        self.translate_button.state(["disabled"])
        self.set_status("Translating...")
        source, script = self.source.get(), self.script.get()
        threading.Thread(target=self._translate_in_background, args=(text, source, script), daemon=True).start()

    def _translate_in_background(self, text: str, source: str, script: str) -> None:
        try:
            result = self.client.translate(text, source, script)
            self.root.after(0, self.show_result, text, result)
        except TranslationError as error:
            self.root.after(0, self.show_error, str(error))

    def show_result(self, text: str, result) -> None:
        self.translate_button.state(["!disabled"])
        self.output.configure(state="normal")
        self.output.delete("1.0", "end")
        self.output.insert("1.0", result.translation, "rtl" if result.script == "arabic" else ())
        self.output.configure(state="disabled")
        self.set_status(f"{result.source_language or 'Detected'} -> Darija")
        self.history.insert(0, (text, result.translation))
        self.history_list.insert(0, f"{text}  =>  {result.translation}")

    def show_error(self, message: str) -> None:
        self.translate_button.state(["!disabled"])
        self.set_status(message, error=True)

    def copy(self) -> None:
        translation = self.output.get("1.0", "end").strip()
        if translation:
            self.root.clipboard_clear()
            self.root.clipboard_append(translation)
            self.set_status("Translation copied")

    def set_status(self, message: str, error: bool = False) -> None:
        self.status.configure(text=message, foreground="#b3261e" if error else "#687776")


def main() -> None:
    root = tk.Tk()
    TranslatorApp(root, DarijaTranslatorClient(ClientConfig.load()))
    root.mainloop()


if __name__ == "__main__":
    main()
