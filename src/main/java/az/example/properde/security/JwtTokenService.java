package az.example.properde.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtTokenService {
    private final String secret;
    private final long ttlSeconds;

    public JwtTokenService(
            @Value("${app.jwt-secret:perde-local-dev-secret-change-me}") String secret,
            @Value("${app.jwt-ttl-seconds:86400}") long ttlSeconds) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long adminId, String username) {
        long exp = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + adminId + "\",\"username\":\"" + escape(username) + "\",\"role\":\"ADMIN\",\"exp\":" + exp + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            return claims.exp() > Instant.now().getEpochSecond();
        } catch (Exception ignored) {
            return false;
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
        if (!expected.equals(parts[2])) {
            throw new IllegalArgumentException("Invalid signature");
        }
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> values = simpleJson(json);
        long exp = Long.parseLong(values.getOrDefault("exp", "0"));
        return new Claims(Long.parseLong(values.get("sub")), values.get("username"), exp);
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

    public record Claims(Long adminId, String username, long exp) {}
}
