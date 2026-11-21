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
        JwtTokenService.Claims claims = jwtTokenService.validClaims(token);
        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Admin token required\"}");
            return false;
        }
        request.setAttribute(ADMIN_CLAIMS_ATTRIBUTE, claims);
        if (claims.demo() && !demoMayAccess(request.getMethod(), path)) {
            // The public demo admin can look at the panel but never change data or see private records.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + DEMO_READ_ONLY_MESSAGE + "\",\"demo\":true}");
            return false;
        }
        return true;
    }

    /** Request attribute holding the {@link JwtTokenService.Claims} of the admin token (admin routes only). */
    public static final String ADMIN_CLAIMS_ATTRIBUTE = "adminClaims";

    public static final String DEMO_READ_ONLY_MESSAGE =
            "Demo rejimi: dəyişikliklər saxlanılmır (demo admin yalnız baxa bilər).";

    /** Admin routes the read-only demo admin cannot even read (raw visitor IPs and user agents). */
    private static final Set<String> DEMO_HIDDEN_READS = Set.of("/api/admin/visitors");

    /** The demo admin may only read (GET/HEAD), and not the routes in {@link #DEMO_HIDDEN_READS}. */
    public static boolean demoMayAccess(String method, String rawPath) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!m.equals("GET") && !m.equals("HEAD")) {
            return false;
        }
        String path = normalize(rawPath);
        return DEMO_HIDDEN_READS.stream().noneMatch(hidden -> startsWithSegment(path, hidden));
    }

    /** True when this request carries the read-only demo admin token (set by {@link #preHandle}). */
    public static boolean isDemoRequest(HttpServletRequest request) {
        return request != null && request.getAttribute(ADMIN_CLAIMS_ATTRIBUTE) instanceof JwtTokenService.Claims claims
                && claims.demo();
    }

    /** Username from the admin token, or null (no token / public route). */
    public static String tokenUsername(HttpServletRequest request) {
        return request != null && request.getAttribute(ADMIN_CLAIMS_ATTRIBUTE) instanceof JwtTokenService.Claims claims
                ? claims.username() : null;
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
