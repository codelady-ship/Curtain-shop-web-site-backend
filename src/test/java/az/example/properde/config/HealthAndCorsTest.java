package az.example.properde.config;

import az.example.properde.controller.HealthController;
import az.example.properde.security.AdminAuthInterceptor;
import az.example.properde.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Render health check and the GitHub Pages frontend origin, without a database. */
class HealthAndCorsTest {
    private static final String PAGES_ORIGIN = "https://codelady-ship.github.io";

    private final AdminAuthInterceptor interceptor =
            new AdminAuthInterceptor(new JwtTokenService("unit-test-secret-0123456789-abcdefghij", 3600));
    private final WebConfig webConfig = new WebConfig(interceptor, "http://localhost:3000," + PAGES_ORIGIN, "./uploads");

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController())
            .addInterceptors(interceptor)
            .addFilters(webConfig.corsFilterRegistration().getFilter())
            .build();

    @Test
    void healthIsPublicAndUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void pagesOriginPassesCorsPreflight() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", PAGES_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PAGES_ORIGIN));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
