package ma.aui.translator;

import ma.aui.translator.llm.Script;
import ma.aui.translator.llm.TranslationException;
import ma.aui.translator.llm.TranslationResult;
import ma.aui.translator.llm.TranslationService;
import ma.aui.translator.rest.TranslatorApplication;
import ma.aui.translator.security.BasicAuthModule;
import ma.aui.translator.security.CredentialValidator;
import ma.aui.translator.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslatorApiTest {

    private static final String VALID_AUTH = basic("tester", "secret");
    private static final AtomicReference<TranslationService> BEHAVIOUR = new AtomicReference<>();
    private static EmbeddedServer server;
    private static String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() throws Exception {
        TranslationService delegating = (text, source, script) -> BEHAVIOUR.get().translate(text, source, script);
        server = new EmbeddedServer(0, new TranslatorApplication(delegating, true),
                new BasicAuthModule(new CredentialValidator("tester", "secret")));
        baseUrl = "http://localhost:" + server.start() + "/api";
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
    }

    @BeforeEach
    void defaultBehaviour() {
        BEHAVIOUR.set((text, source, script) ->
                new TranslationResult(script == Script.LATIN ? "salam" : "سلام", "English", "test-model"));
    }

    @Test
    void validRequestReturnsTranslation() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", VALID_AUTH, "application/json");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"translation\":\"سلام\""), response.body());
        assertTrue(response.body().contains("\"targetLanguage\":\"Moroccan Arabic (Darija)\""));
        assertTrue(response.body().contains("\"sourceLanguage\":\"English\""));
    }

    @Test
    void latinScriptIsPassedToTheService() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hello\",\"script\":\"latin\"}", VALID_AUTH, "application/json");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"translation\":\"salam\""), response.body());
    }

    @Test
    void getWithQueryParameterWorks() throws Exception {
        String url = baseUrl + "/translate?text=" + URLEncoder.encode("Good morning", StandardCharsets.UTF_8);
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", VALID_AUTH).GET());
        assertEquals(200, response.statusCode());
    }

    @Test
    void missingCredentialsAreRejected() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", null, "application/json");
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic realm="));
        assertTrue(response.body().contains("Authentication required"), response.body());
    }

    @Test
    void scriptClientsGet401WithoutBrowserChallenge() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/translate"))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Authorization", basic("tester", "wrong"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Hello\"}")));
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", basic("tester", "wrong"), "application/json");
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Invalid username or password"), response.body());
    }

    @Test
    void malformedAuthorizationHeaderIsRejected() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", "Basic not-base64!!", "application/json");
        assertEquals(401, response.statusCode());
    }

    @Test
    void emptyTextIsBadRequest() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"   \"}", VALID_AUTH, "application/json");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("must not be empty"), response.body());
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        HttpResponse<String> response = post("", VALID_AUTH, "application/json");
        assertEquals(400, response.statusCode());
    }

    @Test
    void tooLongTextIsBadRequest() throws Exception {
        String longText = "a".repeat(5001);
        HttpResponse<String> response = post("{\"text\":\"" + longText + "\"}", VALID_AUTH, "application/json");
        assertEquals(400, response.statusCode());
    }

    @Test
    void invalidScriptIsBadRequest() throws Exception {
        HttpResponse<String> response = post("{\"text\":\"Hi\",\"script\":\"cyrillic\"}", VALID_AUTH, "application/json");
        assertEquals(400, response.statusCode());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        HttpResponse<String> response = post("{\"text\": ", VALID_AUTH, "application/json");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Malformed JSON"), response.body());
    }

    @Test
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        HttpResponse<String> response = post("text=Hello", VALID_AUTH, "application/x-www-form-urlencoded");
        assertEquals(415, response.statusCode());
    }

    @Test
    void llmOverloadMapsTo503() throws Exception {
        BEHAVIOUR.set((text, source, script) -> {
            throw new TranslationException(TranslationException.Reason.UNAVAILABLE, "overloaded");
        });
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", VALID_AUTH, "application/json");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("overloaded"));
    }

    @Test
    void llmRejectionMapsTo502() throws Exception {
        BEHAVIOUR.set((text, source, script) -> {
            throw new TranslationException(TranslationException.Reason.UPSTREAM_REJECTED, "API key not valid");
        });
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", VALID_AUTH, "application/json");
        assertEquals(502, response.statusCode());
    }

    @Test
    void unexpectedErrorMapsTo500WithoutDetails() throws Exception {
        BEHAVIOUR.set((text, source, script) -> {
            throw new IllegalStateException("database password is hunter2");
        });
        HttpResponse<String> response = post("{\"text\":\"Hello\"}", VALID_AUTH, "application/json");
        assertEquals(500, response.statusCode());
        assertTrue(!response.body().contains("hunter2"));
    }

    @Test
    void healthIsPublic() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    void corsPreflightIsAllowedWithoutCredentials() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/translate"))
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
        assertTrue(response.statusCode() == 200 || response.statusCode() == 204, "status " + response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void unknownEndpointIs404() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/nothing"))
                .header("Authorization", VALID_AUTH).GET());
        assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> post(String body, String auth, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/translate"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (auth != null) {
            builder.header("Authorization", auth);
        }
        return send(builder);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
