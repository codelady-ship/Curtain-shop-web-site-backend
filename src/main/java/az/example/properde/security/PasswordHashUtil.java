package az.example.properde.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Admin password hashing. New hashes are BCrypt (the salt is inside the hash, so the admins.salt
 * column just holds {@link #BCRYPT_SALT_MARKER}). Old salted SHA-256 hashes are still accepted and
 * should be replaced on the next successful login ({@link #needsRehash(String)}).
 */
public final class PasswordHashUtil {
    public static final String BCRYPT_SALT_MARKER = "bcrypt";
    public static final int MAX_BCRYPT_BYTES = 72;
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private PasswordHashUtil() {}

    public static String hash(String rawPassword) {
        return BCRYPT.encode(String.valueOf(rawPassword));
    }

    public static boolean matches(String rawPassword, String salt, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            // BCrypt would silently compare only the first 72 bytes; such passwords can never be set
            // (validation rejects them), so a longer input must not match.
            if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
                return false;
            }
            return BCRYPT.matches(rawPassword, storedHash);
        }
        return MessageDigest.isEqual(
                legacySha256(rawPassword, salt).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean needsRehash(String storedHash) {
        return !isBcrypt(storedHash);
    }

    private static boolean isBcrypt(String storedHash) {
        return storedHash != null && storedHash.matches("^[$]2[aby]?[$][0-9]{2}[$].{53}$");
    }

    /** Old format: hex(SHA-256(salt + ":" + password)). Kept only to verify existing hashes. */
    static String legacySha256(String rawPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((String.valueOf(salt) + ":" + String.valueOf(rawPassword)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hash failed", ex);
        }
    }
}
