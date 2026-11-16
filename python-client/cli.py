"""Command-line client: python cli.py "text"  or  python cli.py  (interactive)."""
import argparse
import sys

from darija_client import ClientConfig, DarijaTranslatorClient, TranslationError


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate text into Moroccan Darija via the REST API")
    parser.add_argument("text", nargs="?", help="text to translate (omit for interactive mode)")
    parser.add_argument("--from", dest="source", default="auto", help="source language (default: auto-detect)")
    parser.add_argument("--latin", action="store_true", help="write Darija in Latin letters (Arabizi)")
    args = parser.parse_args()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    client = DarijaTranslatorClient(ClientConfig.load())
    script = "latin" if args.latin else "arabic"

    def run(text: str) -> bool:
        try:
            result = client.translate(text, args.source, script)
        except TranslationError as error:
            print(f"Error: {error}", file=sys.stderr)
            return False
        print(f"[{result.source_language or 'detected'} -> Darija]")
        print(result.translation)
        return True

    if args.text:
        return 0 if run(args.text) else 1

    print("Darija Translator - type text and press Enter (empty line or Ctrl+C to quit)")
    try:
        while text := input("> ").strip():
            run(text)
    except (KeyboardInterrupt, EOFError):
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
