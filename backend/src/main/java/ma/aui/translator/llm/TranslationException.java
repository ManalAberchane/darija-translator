package ma.aui.translator.llm;

public class TranslationException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED(503),
        RATE_LIMITED(503),
        UNAVAILABLE(503),
        UPSTREAM_REJECTED(502),
        UPSTREAM_ERROR(502),
        TIMEOUT(504);

        private final int httpStatus;

        Reason(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    private final Reason reason;

    public TranslationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TranslationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public boolean isRetryable() {
        return reason == Reason.RATE_LIMITED || reason == Reason.UNAVAILABLE || reason == Reason.TIMEOUT;
    }

    public Reason reason() {
        return reason;
    }
}
