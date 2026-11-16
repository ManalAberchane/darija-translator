import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
const api = await import("data:text/javascript;base64," + Buffer.from(source).toString("base64"));
const settings = { serverUrl: "http://server:8080/", username: "manal", password: "pässwörd" };

function mockFetch(responder) {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return responder(url, options);
  };
  return calls;
}

const json = (status, body) => ({ ok: status < 300, status, json: async () => body });

test("base64 encodes UTF-8 credentials like the server expects", () => {
  assert.equal(api.base64Utf8("manal:pässwörd"), Buffer.from("manal:pässwörd").toString("base64"));
  assert.equal(api.base64Utf8("ab"), "YWI=");
  assert.equal(api.base64Utf8("a"), "YQ==");
});

test("translate posts JSON with Basic auth and the script-client header", async () => {
  const calls = mockFetch(() => json(200, { translation: "سلام", script: "arabic" }));
  const result = await api.translate(settings, { text: "Hello", script: "arabic" });
  assert.equal(result.translation, "سلام");
  assert.equal(calls[0].url, "http://server:8080/api/translate");
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.headers.Authorization, "Basic " + Buffer.from("manal:pässwörd").toString("base64"));
  assert.equal(calls[0].options.headers["X-Requested-With"], "XMLHttpRequest");
  assert.deepEqual(JSON.parse(calls[0].options.body), { text: "Hello", sourceLanguage: "auto", script: "arabic" });
});

test("translate surfaces the API error message", async () => {
  mockFetch(() => json(401, { message: "Invalid username or password" }));
  await assert.rejects(api.translate(settings, { text: "Hi" }), /Invalid username or password/);
});

test("translate requires settings", async () => {
  await assert.rejects(api.translate({ serverUrl: "", username: "", password: "" }, { text: "Hi" }), /Open Settings/);
});

test("network failure gives a helpful message", async () => {
  mockFetch(() => { throw new TypeError("Network request failed"); });
  await assert.rejects(api.translate(settings, { text: "Hi" }), /Cannot reach http:\/\/server:8080/);
});

test("checkConnection detects wrong credentials", async () => {
  mockFetch((url) => url.endsWith("/health") ? json(200, { llmConfigured: true }) : json(401, {}));
  await assert.rejects(api.checkConnection(settings), /username or password is wrong/);
});

test("checkConnection succeeds when authenticated", async () => {
  mockFetch((url) => url.endsWith("/health") ? json(200, { llmConfigured: true }) : json(400, {}));
  assert.equal(await api.checkConnection(settings), "Connected and authenticated");
});
