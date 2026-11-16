package ma.aui.translator.security;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.callback.CallerPrincipalCallback;
import jakarta.security.auth.message.callback.GroupPrincipalCallback;
import jakarta.security.auth.message.module.ServerAuthModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * HTTP Basic authentication implemented as a Jakarta Authentication {@link ServerAuthModule}
 * (Servlet Container Profile). The container calls {@link #validateRequest} before every
 * request reaches the JAX-RS resources.
 */
public class BasicAuthModule implements ServerAuthModule {

    public static final String REALM = "Darija Translator";
    public static final String ROLE = "translator";
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/health");

    private final CredentialValidator credentialValidator;
    private CallbackHandler handler;

    public BasicAuthModule(CredentialValidator credentialValidator) {
        this.credentialValidator = credentialValidator;
    }

    @Override
    public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
                           CallbackHandler handler, Map<String, Object> options) {
        this.handler = handler;
    }

    @Override
    public Class<?>[] getSupportedMessageTypes() {
        return new Class<?>[]{HttpServletRequest.class, HttpServletResponse.class};
    }

    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject)
            throws AuthException {
        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();

        if (isPublic(request)) {
            return AuthStatus.SUCCESS;
        }

        String[] credentials = decodeBasicHeader(request.getHeader("Authorization"));
        if (credentials != null && credentialValidator.isValid(credentials[0], credentials[1])) {
            notifyContainer(clientSubject, credentials[0]);
            return AuthStatus.SUCCESS;
        }

        String message = credentials == null
                ? "Authentication required: use HTTP Basic authentication"
                : "Invalid username or password";
        sendUnauthorized(response, message);
        return AuthStatus.SEND_FAILURE;
    }

    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) {
        return AuthStatus.SEND_SUCCESS;
    }

    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) {
        if (subject != null) {
            subject.getPrincipals().clear();
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_PATHS.contains(path);
    }

    static String[] decodeBasicHeader(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return null;
        }
        return new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
    }

    private void notifyContainer(Subject clientSubject, String username) throws AuthException {
        Callback[] callbacks = {
                new CallerPrincipalCallback(clientSubject, username),
                new GroupPrincipalCallback(clientSubject, new String[]{ROLE})
        };
        try {
            handler.handle(callbacks);
        } catch (Exception e) {
            AuthException authException = new AuthException("Container rejected the authenticated caller");
            authException.initCause(e);
            throw authException;
        }
    }

    private static void sendUnauthorized(HttpServletResponse response, String message) throws AuthException {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
            response.flushBuffer();
        } catch (IOException e) {
            AuthException authException = new AuthException("Could not write 401 response");
            authException.initCause(e);
            throw authException;
        }
    }
}
