const BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

export function base64Utf8(value) {
  const bytes = new TextEncoder().encode(value);
  let output = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const [a, b = 0, c = 0] = [bytes[i], bytes[i + 1], bytes[i + 2]];
    const triple = (a << 16) | (b << 8) | c;
    output += BASE64[(triple >> 18) & 63] + BASE64[(triple >> 12) & 63];
    output += i + 1 < bytes.length ? BASE64[(triple >> 6) & 63] : "=";
    output += i + 2 < bytes.length ? BASE64[triple & 63] : "=";
  }
  return output;
}

function endpoint(serverUrl, path) {
  return serverUrl.trim().replace(/\/+$/, "") + path;
}

async function call(settings, path, options = {}, timeoutMs = 60000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(endpoint(settings.serverUrl, path), {
      ...options,
      signal: controller.signal,
      headers: {
        Authorization: "Basic " + base64Utf8(`${settings.username}:${settings.password}`),
        "X-Requested-With": "XMLHttpRequest",
        Accept: "application/json",
        ...(options.headers || {})
      }
    });
  } catch (error) {
    if (error.name === "AbortError") {
      throw new Error("The server took too long to answer");
    }
    throw new Error(`Cannot reach ${settings.serverUrl}. Check the URL and that the phone and laptop share a network.`);
  } finally {
    clearTimeout(timer);
  }
}

async function errorMessage(response) {
  try {
    const body = await response.json();
    return body.message || `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}

export async function translate(settings, { text, sourceLanguage = "auto", script = "arabic" }) {
  if (!settings.serverUrl || !settings.username || !settings.password) {
    throw new Error("Open Settings and enter the server URL, username and password");
  }
  const response = await call(settings, "/api/translate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, sourceLanguage, script })
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return response.json();
}

export async function checkConnection(settings) {
  const health = await call(settings, "/api/health", {}, 10000);
  if (!health.ok) {
    throw new Error(`Server answered HTTP ${health.status}`);
  }
  const { llmConfigured } = await health.json();
  const auth = await call(settings, "/api/translate", {}, 10000);
  if (auth.status === 401) {
    throw new Error("Server reached, but the username or password is wrong");
  }
  return llmConfigured ? "Connected and authenticated" : "Connected, but the server has no Gemini API key";
}
