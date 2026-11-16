package ma.aui.translator.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import ma.aui.translator.llm.TranslationException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts every failure into a JSON {@link ErrorResponse} with a meaningful HTTP status.
 */
public final class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class.getName());

    private ErrorMappers() {
    }

    static Response json(int status, String message) {
        Response.Status known = Response.Status.fromStatusCode(status);
        String reason = known != null ? known.getReasonPhrase() : "Error";
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status, reason, message))
                .build();
    }

    public static class TranslationFailure implements ExceptionMapper<TranslationException> {
        @Override
        public Response toResponse(TranslationException e) {
            return json(e.reason().httpStatus(), e.getMessage());
        }
    }

    public static class InvalidJson implements ExceptionMapper<JsonProcessingException> {
        @Override
        public Response toResponse(JsonProcessingException e) {
            return json(400, "Malformed JSON body: expected {\"text\": \"...\"}");
        }
    }

    public static class WebFailure implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException e) {
            int status = e.getResponse().getStatus();
            String message = switch (status) {
                case 404 -> "No such endpoint";
                case 405 -> "HTTP method not allowed on this endpoint";
                case 406 -> "Only application/json responses are supported";
                case 415 -> "Content-Type must be application/json";
                default -> e.getMessage();
            };
            return json(status, message);
        }
    }

    public static class Unexpected implements ExceptionMapper<Throwable> {
        @Override
        public Response toResponse(Throwable e) {
            LOG.log(Level.SEVERE, "Unexpected server error", e);
            return json(500, "Unexpected server error");
        }
    }
}
