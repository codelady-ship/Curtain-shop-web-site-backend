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
 * Registered for /api/** (see WebConfig). {@link #requiresAdmin(String, String)} decides which
 * routes need the admin JWT ("Authorization: Bearer ..."); everything else stays public.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper(); // decodes, strips ;params

    /** Admin auth routes that must work without a token. */
    private static final Set<String> PUBLIC_ADMIN_AUTH = Set.of(
            "/api/admin/auth/login",
            "/api/admin/auth/forgot-password",
            "/api/admin/auth/reset-password");

    /** Public lead forms (POST only). Every other /api/leads route is admin-only. */
    private static final Set<String> PUBLIC_LEAD_POSTS = Set.of(
            "/api/leads",
            "/api/leads/submit",
            "/api/leads/upload");

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

    /** Access policy. Unknown routes under the protected prefixes default to admin-only. */
    public static boolean requiresAdmin(String method, String rawPath) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(m)) {
            return false; // CORS preflight
        }
        String path = normalize(rawPath);

        if (startsWithSegment(path, "/api/admin")) {
            return !("POST".equals(m) && PUBLIC_ADMIN_AUTH.contains(path));
        }
        if (startsWithSegment(path, "/api/leads")) {
            return !("POST".equals(m) && PUBLIC_LEAD_POSTS.contains(path));
        }
        if (startsWithSegment(path, "/api/products")) {
            return !("GET".equals(m) || "HEAD".equals(m));
        }
        if (startsWithSegment(path, "/api/files")) {
            return true;
        }
        if (startsWithSegment(path, "/api/analytics/dashboard")) {
            return true;
        }
        return false; // banners, partners, health, analytics hit, ...
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
