const HISTORY_LIMIT = 25;
const SPEECH_LANGUAGES = {
  English: "en-US",
  French: "fr-FR",
  Spanish: "es-ES",
  German: "de-DE",
  Italian: "it-IT",
  "Modern Standard Arabic": "ar-MA",
  Portuguese: "pt-PT",
  Korean: "ko-KR"
};

const $ = (id) => document.getElementById(id);
const ui = {
  settings: $("settings"),
  settingsToggle: $("settingsToggle"),
  serverUrl: $("serverUrl"),
  username: $("username"),
  password: $("password"),
  settingsStatus: $("settingsStatus"),
  sourceLanguage: $("sourceLanguage"),
  sourceText: $("sourceText"),
  charCount: $("charCount"),
  translateButton: $("translateButton"),
  micButton: $("micButton"),
  resultBlock: $("resultBlock"),
  resultMeta: $("resultMeta"),
  resultText: $("resultText"),
  status: $("status"),
  history: $("history"),
  historyEmpty: $("historyEmpty")
};

let script = "arabic";
let lastResult = null;
let recognition = null;

function setStatus(element, message, kind = "") {
  element.textContent = message;
  element.className = `status ${kind}`.trim();
}

function toggleSettings(open) {
  ui.settings.hidden = !open;
  ui.settingsToggle.setAttribute("aria-expanded", String(open));
}

function setScript(value) {
  script = value;
  document.querySelectorAll(".segmented button").forEach((button) => {
    const active = button.dataset.script === value;
    button.classList.toggle("active", active);
    button.setAttribute("aria-checked", String(active));
  });
}

function updateCharCount() {
  ui.charCount.textContent = `${ui.sourceText.value.length} / 5000`;
}

async function readSettingsForm() {
  return {
    serverUrl: ui.serverUrl.value.trim() || "http://localhost:8080",
    username: ui.username.value.trim(),
    password: ui.password.value
  };
}

function isBuiltInHost(url) {
  try {
    const { hostname } = new URL(url);
    return hostname === "localhost" || hostname === "127.0.0.1";
  } catch {
    return false;
  }
}

async function saveSettings() {
  const url = ui.serverUrl.value.trim() || "http://localhost:8080";
  let origin;
  try {
    origin = new URL(url).origin;
  } catch {
    setStatus(ui.settingsStatus, "The API URL is not valid", "error");
    return;
  }
  if (!isBuiltInHost(url)) {
    const granted = await chrome.permissions.request({ origins: [`${origin}/*`] });
    if (!granted) {
      setStatus(ui.settingsStatus, `Chrome permission to contact ${origin} was refused`, "error");
      return;
    }
  }
  await DarijaApi.saveSettings(await readSettingsForm());
  setStatus(ui.settingsStatus, "Settings saved", "ok");
}

async function testConnection() {
  setStatus(ui.settingsStatus, "Checking…");
  try {
    const message = await DarijaApi.checkConnection(await readSettingsForm());
    setStatus(ui.settingsStatus, message, message.startsWith("Connected and") ? "ok" : "error");
  } catch (error) {
    setStatus(ui.settingsStatus, error.message, "error");
  }
}

async function translate({ speakResult = false } = {}) {
  const text = ui.sourceText.value.trim();
  if (!text) {
    setStatus(ui.status, "Enter or select some text first", "error");
    return;
  }
  const settings = await DarijaApi.loadSettings();
  if (!settings.username || !settings.password) {
    toggleSettings(true);
    setStatus(ui.settingsStatus, "Enter the API username and password, then Save", "error");
    return;
  }

  ui.translateButton.disabled = true;
  ui.translateButton.textContent = "Translating…";
  setStatus(ui.status, "");
  try {
    const result = await DarijaApi.translate(settings, {
      text,
      sourceLanguage: ui.sourceLanguage.value,
      script
    });
    lastResult = result;
    showResult(result);
    await addToHistory({ text, translation: result.translation, script: result.script, source: result.sourceLanguage });
    if (speakResult) {
      speak();
    }
  } catch (error) {
    setStatus(ui.status, error.message, "error");
  } finally {
    ui.translateButton.disabled = false;
    ui.translateButton.textContent = "Translate to Darija";
  }
}

function showResult(result) {
  ui.resultBlock.hidden = false;
  ui.resultText.textContent = result.translation;
  ui.resultText.dir = result.script === "latin" ? "ltr" : "rtl";
  ui.resultMeta.textContent = `${result.sourceLanguage || "Detected"} → Darija`;
}

async function copyTranslation() {
  if (!lastResult) {
    return;
  }
  try {
    await navigator.clipboard.writeText(lastResult.translation);
  } catch {
    const helper = document.createElement("textarea");
    helper.value = lastResult.translation;
    document.body.appendChild(helper);
    helper.select();
    document.execCommand("copy");
    helper.remove();
  }
  setStatus(ui.status, "Translation copied", "ok");
}

function speak() {
  if (!lastResult) {
    return;
  }
  const wantsArabic = lastResult.script !== "latin";
  chrome.tts.getVoices((voices) => {
    const arabicVoice = voices.find((voice) => (voice.lang || "").toLowerCase().startsWith("ar"));
    if (wantsArabic && !arabicVoice) {
      setStatus(ui.status,
        "No Arabic voice is installed. On Windows: Settings → Time & language → Speech → Add voices → Arabic.",
        "error");
      return;
    }
    chrome.tts.speak(lastResult.translation, {
      lang: wantsArabic ? arabicVoice.lang : "fr-FR",
      voiceName: wantsArabic ? arabicVoice.voiceName : undefined,
      rate: 0.95
    });
  });
}

function toggleMicrophone() {
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    setStatus(ui.status, "Speech recognition is not available in this browser", "error");
    return;
  }
  if (recognition) {
    recognition.stop();
    return;
  }
  recognition = new Recognition();
  recognition.lang = SPEECH_LANGUAGES[ui.sourceLanguage.value] || navigator.language || "en-US";
  recognition.interimResults = true;
  recognition.maxAlternatives = 1;

  let finalText = "";
  recognition.onstart = () => {
    ui.micButton.classList.add("recording");
    setStatus(ui.status, `Listening (${recognition.lang})… click 🎤 again to stop`);
  };
  recognition.onresult = (event) => {
    let interim = "";
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const chunk = event.results[i][0].transcript;
      if (event.results[i].isFinal) {
        finalText += chunk;
      } else {
        interim += chunk;
      }
    }
    ui.sourceText.value = (finalText + interim).trim();
    updateCharCount();
  };
  recognition.onerror = (event) => {
    if (event.error === "not-allowed" || event.error === "service-not-allowed") {
      setStatus(ui.status, "Microphone permission needed: allow it in the tab that just opened, then try again.", "error");
      chrome.tabs.create({ url: chrome.runtime.getURL("microphone.html") });
    } else if (event.error !== "aborted") {
      setStatus(ui.status, `Speech recognition error: ${event.error}`, "error");
    }
  };
  recognition.onend = () => {
    ui.micButton.classList.remove("recording");
    recognition = null;
    if (finalText.trim()) {
      ui.sourceText.value = finalText.trim();
      translate({ speakResult: true });
    } else if (!ui.status.classList.contains("error")) {
      setStatus(ui.status, "");
    }
  };
  recognition.start();
}

async function loadHistory() {
  const { history } = await chrome.storage.local.get("history");
  return history || [];
}

async function addToHistory(entry) {
  const history = await loadHistory();
  history.unshift({ ...entry, at: Date.now() });
  await chrome.storage.local.set({ history: history.slice(0, HISTORY_LIMIT) });
  renderHistory();
}

async function renderHistory() {
  const history = await loadHistory();
  ui.history.replaceChildren(...history.map((item) => {
    const li = document.createElement("li");
    const src = document.createElement("div");
    src.className = "src";
    src.textContent = item.text;
    const dst = document.createElement("div");
    dst.className = "dst";
    dst.dir = item.script === "latin" ? "ltr" : "rtl";
    dst.textContent = item.translation;
    li.append(src, dst);
    li.title = new Date(item.at).toLocaleString();
    li.addEventListener("click", () => {
      ui.sourceText.value = item.text;
      updateCharCount();
      setScript(item.script || "arabic");
      lastResult = { translation: item.translation, script: item.script, sourceLanguage: item.source };
      showResult(lastResult);
    });
    return li;
  }));
  ui.historyEmpty.hidden = history.length > 0;
}

async function consumePendingSelection() {
  const { pendingTranslation } = await chrome.storage.session.get("pendingTranslation");
  if (!pendingTranslation || !pendingTranslation.text) {
    return;
  }
  await chrome.storage.session.remove("pendingTranslation");
  ui.sourceText.value = pendingTranslation.text.slice(0, 5000);
  updateCharCount();
  translate();
}

async function init() {
  const settings = await DarijaApi.loadSettings();
  ui.serverUrl.value = settings.serverUrl;
  ui.username.value = settings.username;
  ui.password.value = settings.password;
  if (!settings.username) {
    toggleSettings(true);
    setStatus(ui.settingsStatus, "First run: enter the REST API credentials and click Save");
  }

  ui.settingsToggle.addEventListener("click", () => toggleSettings(ui.settings.hidden));
  $("saveSettings").addEventListener("click", saveSettings);
  $("testConnection").addEventListener("click", testConnection);
  ui.translateButton.addEventListener("click", () => translate());
  $("copyButton").addEventListener("click", copyTranslation);
  $("speakButton").addEventListener("click", speak);
  ui.micButton.addEventListener("click", toggleMicrophone);
  $("clearHistory").addEventListener("click", async () => {
    await chrome.storage.local.set({ history: [] });
    renderHistory();
  });
  document.querySelectorAll(".segmented button").forEach((button) =>
    button.addEventListener("click", () => setScript(button.dataset.script)));
  ui.sourceText.addEventListener("input", updateCharCount);
  ui.sourceText.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      translate();
    }
  });
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "session" && changes.pendingTranslation && changes.pendingTranslation.newValue) {
      consumePendingSelection();
    }
  });

  renderHistory();
  consumePendingSelection();
}

init();
