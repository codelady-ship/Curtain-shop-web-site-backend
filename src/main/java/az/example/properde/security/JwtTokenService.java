package az.example.properde.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtTokenService {
    static final int MIN_SECRET_BYTES = 32;

    private final String secret;
    private final long ttlSeconds;

    public JwtTokenService(
            @Value("${app.jwt-secret:}") String secret,
            @Value("${app.jwt-ttl-seconds:86400}") long ttlSeconds) {
        // No default secret: refuse to start without a real one (JWT_SECRET, at least 32 bytes).
        // Local development can use the "dev" profile (application-dev.yml).
        if (!StringUtils.hasText(secret) || secret.trim().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and at least " + MIN_SECRET_BYTES
                    + " bytes long (or run locally with SPRING_PROFILES_ACTIVE=dev).");
        }
        this.secret = secret.trim();
        this.ttlSeconds = ttlSeconds;
    }

    /** Role claim of a full admin token. */
    public static final String ROLE_ADMIN = "ADMIN";
    /** Role claim of the public read-only demo admin: AdminAuthInterceptor refuses its writes. */
    public static final String ROLE_DEMO = "DEMO";

    public String issue(Long adminId, String username) {
        return issue(adminId, username, ROLE_ADMIN);
    }

    public String issue(Long adminId, String username, String role) {
        long exp = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String safeRole = ROLE_DEMO.equals(role) ? ROLE_DEMO : ROLE_ADMIN;
        String payload = b64("{\"sub\":\"" + adminId + "\",\"username\":\"" + escape(username) + "\",\"role\":\"" + safeRole + "\",\"exp\":" + exp + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public boolean isValid(String token) {
        return validClaims(token) != null;
    }

    /** The claims of a correctly signed, unexpired token, otherwise null. */
    public Claims validClaims(String token) {
        try {
            Claims claims = parse(token);
            return claims.exp() > Instant.now().getEpochSecond() ? claims : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public Claims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Token is empty");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format");
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid signature");
        }
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> values = simpleJson(json);
        long exp = Long.parseLong(values.getOrDefault("exp", "0"));
        // Tokens issued before the role claim existed are full admin tokens.
        String role = ROLE_DEMO.equals(values.get("role")) ? ROLE_DEMO : ROLE_ADMIN;
        return new Claims(Long.parseLong(values.get("sub")), values.get("username"), exp, role);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Token signing failed", ex);
        }
    }

    private String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> simpleJson(String json) {
        Map<String, String> out = new HashMap<>();
        String body = json.trim().replaceAll("^\\{|}$", "");
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                out.put(key, value);
            }
        }
        return out;
    }

    public record Claims(Long adminId, String username, long exp, String role) {
        public boolean demo() {
            return ROLE_DEMO.equals(role);
        }
    }
}
