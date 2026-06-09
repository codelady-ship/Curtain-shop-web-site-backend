package az.example.properde.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordHashUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHashUtil() {}

    public static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((String.valueOf(salt) + ":" + String.valueOf(password)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hash failed", ex);
        }
    }

    public static boolean matches(String rawPassword, String salt, String expectedHash) {
        return MessageDigest.isEqual(hash(rawPassword, salt).getBytes(StandardCharsets.UTF_8), String.valueOf(expectedHash).getBytes(StandardCharsets.UTF_8));
    }
}
