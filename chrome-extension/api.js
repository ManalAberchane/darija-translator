const DarijaApi = (() => {
  const DEFAULT_SETTINGS = { serverUrl: "http://localhost:8080", username: "", password: "" };

  async function loadSettings() {
    const { settings } = await chrome.storage.local.get("settings");
    return { ...DEFAULT_SETTINGS, ...(settings || {}) };
  }

  async function saveSettings(settings) {
    await chrome.storage.local.set({ settings });
  }

  function endpoint(serverUrl, path) {
    return serverUrl.replace(/\/+$/, "") + path;
  }

  function authHeader(settings) {
    const bytes = new TextEncoder().encode(`${settings.username}:${settings.password}`);
    return "Basic " + btoa(String.fromCharCode(...bytes));
  }

  async function readError(response) {
    try {
      const body = await response.json();
      return body.message || `HTTP ${response.status}`;
    } catch {
      return `HTTP ${response.status}`;
    }
  }

  async function request(settings, path, options = {}) {
    let response;
    try {
      response = await fetch(endpoint(settings.serverUrl, path), {
        ...options,
        headers: { Authorization: authHeader(settings), "X-Requested-With": "XMLHttpRequest", ...(options.headers || {}) }
      });
    } catch {
      throw new Error(`Cannot reach the server at ${settings.serverUrl}. Is the backend running?`);
    }
    return response;
  }

  async function translate(settings, { text, sourceLanguage, script }) {
    const response = await request(settings, "/api/translate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, sourceLanguage, script })
    });
    if (!response.ok) {
      const message = await readError(response);
      throw new Error(response.status === 401 ? `${message}. Check the username and password in settings.` : message);
    }
    return response.json();
  }

  async function checkConnection(settings) {
    const health = await request(settings, "/api/health");
    if (!health.ok) {
      throw new Error(`Server answered HTTP ${health.status}`);
    }
    const status = await health.json();
    const auth = await request(settings, "/api/translate");
    if (auth.status === 401) {
      throw new Error("Server reached, but the username or password is wrong");
    }
    return status.llmConfigured
      ? "Connected and authenticated"
      : "Connected, but the server has no Gemini API key configured";
  }

  return { loadSettings, saveSettings, translate, checkConnection };
})();
