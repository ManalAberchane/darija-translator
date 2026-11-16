import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

from darija_client import ClientConfig, DarijaTranslatorClient, TranslationError


class FakeApi(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.headers.get("Authorization") != "Basic dXNlcjpwYXNz":
            return self.reply(401, {"status": 401, "message": "Invalid username or password"})
        if not body["text"].strip():
            return self.reply(400, {"status": 400, "message": "The 'text' field must not be empty"})
        self.reply(200, {"translation": "سلام", "sourceLanguage": "English", "targetLanguage": "Moroccan Arabic (Darija)",
                         "script": body["script"], "model": "fake"})

    def reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class DarijaClientTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), FakeApi)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def client(self, password="pass", url=None):
        return DarijaTranslatorClient(ClientConfig(url or self.url, "user", password), timeout=5)

    def test_successful_translation(self):
        result = self.client().translate("Hello", script="latin")
        self.assertEqual("سلام", result.translation)
        self.assertEqual("English", result.source_language)
        self.assertEqual("latin", result.script)

    def test_wrong_password_raises_with_api_message(self):
        with self.assertRaisesRegex(TranslationError, "401: Invalid username or password"):
            self.client(password="wrong").translate("Hello")

    def test_empty_text_raises_bad_request(self):
        with self.assertRaisesRegex(TranslationError, "400"):
            self.client().translate("  ")

    def test_unreachable_server(self):
        with self.assertRaisesRegex(TranslationError, "Cannot reach"):
            self.client(url="http://127.0.0.1:1").translate("Hello")


if __name__ == "__main__":
    unittest.main()
