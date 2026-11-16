package ma.aui.translator.rest;

public record ErrorResponse(int status, String error, String message) {
}
