package az.example.properde.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Access policy: admin-only routes return 401 without a valid token and pass with one; public routes stay open. */
class AdminAuthInterceptorTest {
    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";
    private final JwtTokenService jwt = new JwtTokenService(SECRET, 3600);
    private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(jwt);

    private MockHttpServletResponse run(String method, String path, String authorization, boolean expectedPass) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(expectedPass, interceptor.preHandle(request, response, new Object()), method + " " + path);
        return response;
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/leads",
            "GET,/api/leads/stats",
            "PATCH,/api/leads/1/status",
            "PATCH,/api/leads/1/contacted",
            "PATCH,/api/leads/1/promo",
            "DELETE,/api/leads/1/soft-delete",
            "GET,/api/leads/1",
            "POST,/api/products",
            "POST,/api/products/upload",
            "PUT,/api/products/1",
            "DELETE,/api/products/1",
            "POST,/api/files/upload",
            "GET,/api/analytics/dashboard",
            "GET,/api/admin/products",
            "GET,/api/admin/auth/profile",
            "PUT,/api/admin/auth/profile",
            "GET,/api/admin/visitors",
            "GET,/api/leads/",
            "GET,/api//leads",
            "GET,/api/Leads",
            "DELETE,/api/products/1/",
            // deny by default: anything not on the public allow-list
            "GET,/api",
            "GET,/api/unknown",
            "GET,/api/visitors",
            "GET,/api/admin/recovery/request",
            "POST,/api/admin/recovery/request",
            "POST,/api/banners",
            "PUT,/api/partners",
            "DELETE,/api/health",
            "GET,/api/analytics/hit",
            "POST,/api/analytics/dashboard",
            "PATCH,/api/products/1",
            "POST,/api/products/all",
            "POST,/api/admin/auth/change-password",
            "GET,/api/leads/submit",
    })
    void adminRoutesNeedAValidToken(String method, String path) throws Exception {
        assertEquals(401, run(method, path, null, false).getStatus());
        assertEquals(401, run(method, path, "Bearer not-a-token", false).getStatus());

        String forged = new JwtTokenService("another-secret-0123456789-abcdefghijkl", 3600).issue(1L, "admin");
        assertEquals(401, run(method, path, "Bearer " + forged, false).getStatus());

        String expired = new JwtTokenService(SECRET, -10).issue(1L, "admin");
        assertEquals(401, run(method, path, "Bearer " + expired, false).getStatus());

        run(method, path, "Bearer " + jwt.issue(1L, "admin"), true);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/products/all",
            "GET,/api/products/filter",
            "GET,/api/products/5",
            "GET,/api/banners",
            "GET,/api/partners",
            "GET,/api/health",
            "POST,/api/analytics/hit",
            "POST,/api/leads",
            "POST,/api/leads/submit",
            "POST,/api/leads/upload",
            "POST,/api/admin/auth/login",
            "POST,/api/admin/auth/forgot-password",
            "POST,/api/admin/auth/reset-password",
            "OPTIONS,/api/leads",
            "OPTIONS,/api/admin/products",
            "OPTIONS,/api/anything",
            "HEAD,/api/products/all",
            "GET,/api/products",
            "GET,/api/banners/",
            "GET,/api//partners",
    })
    void publicRoutesStayOpen(String method, String path) throws Exception {
        run(method, path, null, true);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/admin/auth/login", "PUT,/api/leads/submit", "GET,/api/leads/upload"})
    void publicPathsWithOtherMethodsAreProtected(String method, String path) throws Exception {
        assertEquals(401, run(method, path, null, false).getStatus());
    }
}
