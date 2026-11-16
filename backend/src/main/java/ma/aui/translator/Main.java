package ma.aui.translator;

import ma.aui.translator.config.AppConfig;
import ma.aui.translator.llm.GeminiTranslationService;
import ma.aui.translator.rest.TranslatorApplication;
import ma.aui.translator.security.BasicAuthModule;
import ma.aui.translator.security.CredentialValidator;
import ma.aui.translator.server.EmbeddedServer;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        String apiKey = config.hasGeminiKey() ? config.geminiApiKey() : null;
        if (apiKey == null) {
            System.err.println("WARNING: GEMINI_API_KEY is not set; /api/translate will answer 503 until it is.");
        }
        var translationService = new GeminiTranslationService(
                apiKey, config.geminiModel(), config.geminiFallbackModel(), config.geminiBaseUrl());
        var application = new TranslatorApplication(translationService, apiKey != null);
        var authModule = new BasicAuthModule(new CredentialValidator(config.username(), config.password()));

        EmbeddedServer server = new EmbeddedServer(config.port(), application, authModule);
        int port = server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // JVM is exiting
            }
        }));

        System.out.println();
        System.out.println("Darija Translator is running (model: " + config.geminiModel() + ")");
        System.out.println("  Local:   http://localhost:" + port + "/api/translate");
        for (String address : lanAddresses()) {
            System.out.println("  Network: http://" + address + ":" + port + "/api/translate");
        }
        System.out.println("Press Ctrl+C to stop.");
        server.await();
    }

    private static java.util.List<String> lanAddresses() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(nic -> {
                        try {
                            return nic.isUp() && !nic.isLoopback() && !nic.isVirtual();
                        } catch (SocketException e) {
                            return false;
                        }
                    })
                    .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                    .filter(address -> address instanceof Inet4Address && address.isSiteLocalAddress())
                    .map(java.net.InetAddress::getHostAddress)
                    .toList();
        } catch (SocketException e) {
            return java.util.List.of();
        }
    }
}
