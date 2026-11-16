"""Minimal stand-in for the Gemini generateContent API, used by end-to-end tests
so they can run without an API key or quota. It echoes the text it receives."""
import json
import re
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class FakeGemini(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        prompt = body["contents"][-1]["parts"][0]["text"]
        text = re.search(r"<text>\n(.*)\n</text>", prompt, re.S).group(1)
        prefix = "latin:" if "Latin letters" in prompt else "دارجة:"
        answer = json.dumps({"translation": f"{prefix} {text}", "detectedLanguage": "English"}, ensure_ascii=False)
        payload = json.dumps({"candidates": [{"content": {"parts": [{"text": answer}]}, "finishReason": "STOP"}]})
        data = payload.encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 9090), FakeGemini).serve_forever()
