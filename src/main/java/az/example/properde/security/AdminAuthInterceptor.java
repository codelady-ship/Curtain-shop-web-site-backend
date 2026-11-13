package az.example.properde.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.Locale;
import java.util.Set;

/**
 * Registered for /api and /api/** (see WebConfig). Deny by default: every API route needs the admin
 * JWT ("Authorization: Bearer ...") unless {@link #isPublic(String, String)} lists it explicitly.
 * A new endpoint is therefore admin-only until it is added to the allow-list below.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper(); // decodes, strips ;params

    /** Exact paths readable without a token (GET/HEAD). */
    private static final Set<String> PUBLIC_READS = Set.of(
            "/api/banners",
            "/api/partners",
            "/api/health");

    /** Path prefix readable without a token (GET/HEAD): product list, filter and detail. */
    private static final String PUBLIC_READ_PREFIX = "/api/products";

    /** Exact paths that accept POST without a token: public forms, visit tracking, admin login/recovery. */
    private static final Set<String> PUBLIC_POSTS = Set.of(
            "/api/analytics/hit",
            "/api/leads",
            "/api/leads/submit",
            "/api/leads/upload",
            "/api/admin/auth/login",
            "/api/admin/auth/forgot-password",
            "/api/admin/auth/reset-password");

    private final JwtTokenService jwtTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = PATH_HELPER.getPathWithinApplication(request);
        if (!requiresAdmin(request.getMethod(), path)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        }
        if (!jwtTokenService.isValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Admin token required\"}");
            return false;
        }
        return true;
    }

    public static boolean requiresAdmin(String method, String rawPath) {
        return !isPublic(method, rawPath);
    }

    /** The complete list of routes that work without the admin token. */
    public static boolean isPublic(String method, String rawPath) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String path = normalize(rawPath);
        switch (m) {
            case "OPTIONS":
                return true; // CORS preflight
            case "GET":
            case "HEAD":
                return PUBLIC_READS.contains(path) || startsWithSegment(path, PUBLIC_READ_PREFIX);
            case "POST":
                return PUBLIC_POSTS.contains(path);
            default:
                return false;
        }
    }

    private static String normalize(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.toLowerCase(Locale.ROOT);
    }

    private static boolean startsWithSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
