package ma.aui.translator.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import ma.aui.translator.llm.Script;
import ma.aui.translator.llm.TranslationResult;
import ma.aui.translator.llm.TranslationService;

import java.security.Principal;
import java.util.Locale;
import java.util.logging.Logger;

@Path("/translate")
@Produces(MediaType.APPLICATION_JSON)
public class TranslatorResource {

    private static final Logger LOG = Logger.getLogger(TranslatorResource.class.getName());
    public static final int MAX_TEXT_LENGTH = 5000;
    public static final String TARGET_LANGUAGE = "Moroccan Arabic (Darija)";

    private final TranslationService translationService;

    @Context
    private SecurityContext securityContext;

    @Inject
    public TranslatorResource(TranslationService translationService) {
        this.translationService = translationService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public TranslationResponse translate(TranslationRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required: {\"text\": \"...\"}");
        }
        return translate(request.text(), request.sourceLanguage(), request.script());
    }

    @GET
    public TranslationResponse translateFromQuery(@QueryParam("text") String text,
                                                  @QueryParam("sourceLanguage") String sourceLanguage,
                                                  @QueryParam("script") String script) {
        return translate(text, sourceLanguage, script);
    }

    private TranslationResponse translate(String text, String sourceLanguage, String scriptValue) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("The 'text' field must not be empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("The 'text' field must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        Script script;
        try {
            script = Script.parse(scriptValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        Principal caller = securityContext != null ? securityContext.getUserPrincipal() : null;
        LOG.info(() -> "Translation requested by " + (caller != null ? caller.getName() : "unknown")
                + " (" + text.length() + " chars, script=" + script + ")");
        TranslationResult result = translationService.translate(text.strip(), sourceLanguage, script);
        String source = result.detectedSourceLanguage() != null ? result.detectedSourceLanguage() : sourceLanguage;
        return new TranslationResponse(result.translation(), source, TARGET_LANGUAGE,
                script.name().toLowerCase(Locale.ROOT), result.model());
    }
}
