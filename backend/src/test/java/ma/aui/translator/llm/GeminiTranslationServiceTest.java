package ma.aui.translator.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiTranslationServiceTest {

    private HttpServer fakeGemini;
    private String baseUrl;
    private final Map<String, int[]> statusByModel = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> bodyByModel = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> calledModels = new ArrayList<>();
    private volatile String lastRequestBody;
    private volatile String lastApiKey;

    @BeforeEach
    void start() throws Exception {
        fakeGemini = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeGemini.createContext("/v1beta/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.lastIndexOf('/') + 1, path.indexOf(':'));
            calledModels.add(model);
            lastApiKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = bodyByModel.getOrDefault(model, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusByModel.getOrDefault(model, new int[]{200})[0], body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        fakeGemini.start();
        baseUrl = "http://localhost:" + fakeGemini.getAddress().getPort() + "/v1beta";
    }

    @AfterEach
    void stop() {
        fakeGemini.stop(0);
    }

    private static String success(String translation) {
        String inner = "{\\\"translation\\\":\\\"" + translation + "\\\",\\\"detectedLanguage\\\":\\\"English\\\"}";
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + inner + "\"}]},\"finishReason\":\"STOP\"}]}";
    }

    @Test
    void parsesStructuredAnswerAndSendsKeyInHeader() {
        bodyByModel.put("primary", success("صباح الخير"));
        var service = new GeminiTranslationService("k-123", "primary", null, baseUrl);
        TranslationResult result = service.translate("Good morning", "auto", Script.ARABIC);
        assertEquals("صباح الخير", result.translation());
        assertEquals("English", result.detectedSourceLanguage());
        assertEquals("primary", result.model());
        assertEquals("k-123", lastApiKey);
        assertTrue(lastRequestBody.contains("Arabic script"));
        assertTrue(lastRequestBody.contains("Good morning"));
    }

    @Test
    void latinScriptChangesThePrompt() {
        bodyByModel.put("primary", success("sba7 lkhir"));
        var service = new GeminiTranslationService("k", "primary", null, baseUrl);
        service.translate("Good morning", "English", Script.LATIN);
        assertTrue(lastRequestBody.contains("Latin letters"));
        assertTrue(lastRequestBody.contains("The source language is English"));
    }

    @Test
    void fallsBackWhenPrimaryIsOverloaded() {
        statusByModel.put("primary", new int[]{503});
        bodyByModel.put("primary", "{\"error\":{\"message\":\"high demand\"}}");
        bodyByModel.put("backup", success("مرحبا"));
        var service = new GeminiTranslationService("k", "primary", "backup", baseUrl);
        TranslationResult result = service.translate("Hello", null, Script.ARABIC);
        assertEquals("backup", result.model());
        assertEquals(List.of("primary", "backup"), calledModels);
    }

    @Test
    void doesNotFallBackWhenKeyIsRejected() {
        statusByModel.put("primary", new int[]{400});
        bodyByModel.put("primary", "{\"error\":{\"message\":\"API key not valid\"}}");
        var service = new GeminiTranslationService("bad", "primary", "backup", baseUrl);
        TranslationException e = assertThrows(TranslationException.class,
                () -> service.translate("Hello", null, Script.ARABIC));
        assertEquals(TranslationException.Reason.UPSTREAM_REJECTED, e.reason());
        assertTrue(e.getMessage().contains("API key not valid"));
        assertEquals(List.of("primary"), calledModels);
    }

    @Test
    void quotaExhaustedOnAllModelsIsRateLimited() {
        statusByModel.put("primary", new int[]{429});
        statusByModel.put("backup", new int[]{429});
        var service = new GeminiTranslationService("k", "primary", "backup", baseUrl);
        TranslationException e = assertThrows(TranslationException.class,
                () -> service.translate("Hello", null, Script.ARABIC));
        assertEquals(TranslationException.Reason.RATE_LIMITED, e.reason());
        assertEquals(503, e.reason().httpStatus());
    }

    @Test
    void unreadableAnswerIsUpstreamError() {
        bodyByModel.put("primary", "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not json\"}]}}]}");
        var service = new GeminiTranslationService("k", "primary", null, baseUrl);
        TranslationException e = assertThrows(TranslationException.class,
                () -> service.translate("Hello", null, Script.ARABIC));
        assertEquals(TranslationException.Reason.UPSTREAM_ERROR, e.reason());
    }

    @Test
    void missingKeyIsNotConfigured() {
        var service = new GeminiTranslationService(null, "primary", null, baseUrl);
        TranslationException e = assertThrows(TranslationException.class,
                () -> service.translate("Hello", null, Script.ARABIC));
        assertEquals(TranslationException.Reason.NOT_CONFIGURED, e.reason());
        assertTrue(calledModels.isEmpty());
    }

    @Test
    void unreachableApiIsUpstreamError() {
        var service = new GeminiTranslationService("k", "primary", null, "http://localhost:1/v1beta");
        TranslationException e = assertThrows(TranslationException.class,
                () -> service.translate("Hello", null, Script.ARABIC));
        assertEquals(TranslationException.Reason.UPSTREAM_ERROR, e.reason());
    }
}
