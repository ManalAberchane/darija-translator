package ma.aui.translator.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks a username/password pair against the single account configured for the service.
 * Comparison is constant-time so response timing does not leak how many characters matched.
 */
public class CredentialValidator {

    private final byte[] username;
    private final byte[] password;

    public CredentialValidator(String username, String password) {
        this.username = username.getBytes(StandardCharsets.UTF_8);
        this.password = password.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String candidateUser, String candidatePassword) {
        if (candidateUser == null || candidatePassword == null) {
            return false;
        }
        boolean userMatches = MessageDigest.isEqual(username, candidateUser.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = MessageDigest.isEqual(password, candidatePassword.getBytes(StandardCharsets.UTF_8));
        return userMatches & passwordMatches;
    }
}
