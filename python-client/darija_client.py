"""Client library for the Darija Translator REST API (standard library only)."""
from __future__ import annotations

import base64
import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path


class TranslationError(Exception):
    """Raised when the API cannot be reached or answers with an error."""


@dataclass(frozen=True)
class Translation:
    translation: str
    source_language: str | None
    target_language: str
    script: str
    model: str


@dataclass(frozen=True)
class ClientConfig:
    api_url: str
    username: str
    password: str

    @classmethod
    def load(cls, env_file: Path | None = None) -> "ClientConfig":
        values: dict[str, str] = {}
        env_file = env_file or Path(__file__).with_name(".env")
        if env_file.is_file():
            for line in env_file.read_text(encoding="utf-8-sig").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    values[key.strip()] = value.strip().strip("\"'")
        get = lambda key, default="": os.environ.get(key, values.get(key, default))
        return cls(get("API_URL", "http://localhost:8080").rstrip("/"), get("API_USERNAME"), get("API_PASSWORD"))


class DarijaTranslatorClient:
    def __init__(self, config: ClientConfig, timeout: float = 60):
        self.config = config
        self.timeout = timeout

    def translate(self, text: str, source_language: str = "auto", script: str = "arabic") -> Translation:
        body = json.dumps({"text": text, "sourceLanguage": source_language, "script": script}).encode("utf-8")
        token = base64.b64encode(f"{self.config.username}:{self.config.password}".encode("utf-8")).decode("ascii")
        request = urllib.request.Request(
            f"{self.config.api_url}/api/translate",
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
                "Authorization": f"Basic {token}",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                data = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise TranslationError(f"The API answered {error.code}: {self._error_message(error)}") from None
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            raise TranslationError(
                f"Cannot reach the translation API at {self.config.api_url}. Is the backend running?") from None
        except json.JSONDecodeError:
            raise TranslationError("The API returned an unexpected response") from None

        return Translation(
            translation=data["translation"],
            source_language=data.get("sourceLanguage"),
            target_language=data.get("targetLanguage", "Moroccan Arabic (Darija)"),
            script=data.get("script", script),
            model=data.get("model", ""),
        )

    @staticmethod
    def _error_message(error: urllib.error.HTTPError) -> str:
        try:
            return json.loads(error.read().decode("utf-8")).get("message", error.reason)
        except (ValueError, AttributeError):
            return str(error.reason)
