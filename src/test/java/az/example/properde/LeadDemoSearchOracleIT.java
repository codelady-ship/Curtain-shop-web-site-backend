package az.example.properde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole application over real HTTP against a real PostgreSQL (Liquibase included): the demo admin
 * cannot use GET /api/leads?search= to find out what is behind the masked columns.
 *
 * Needs a database, so it only runs with RUN_DB_TESTS=true and SPRING_DATASOURCE_URL / _USERNAME /
 * _PASSWORD pointing at a throwaway PostgreSQL (the "migrations" job in CI does that).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt-secret=it-only-jwt-secret-0123456789-abcdefghijklmnop",
        "app.admin.username=admin",
        "app.admin.password=it-only-admin-password-1",
        "app.upload-dir=build/it-uploads"
})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeadDemoSearchOracleIT {
    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();
    // Unique per run, so the test also works on a database that already has rows.
    private final String run = String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
    private final String phoneA = "99450" + run;          // 12 digits
    private final String phoneB = "99455" + run;
    private final String emailA = "aynur" + run + "@example.com";
    private final String nameA = "Aynur" + run + " Quliyeva";
    private final String messageA = "Qapi kodu " + run;
    private final String productA = "Blackout" + run;
    private final String productB = "Tul" + run;

    private String demo;
    private String admin;
    private long idA;

    @BeforeAll
    void data() throws Exception {
        idA = json.readTree(send("POST", "/api/leads", null, json.writeValueAsString(Map.of(
                "fullName", nameA, "phone", phoneA, "email", emailA, "message", messageA,
                "source", "ORDER", "requestedProducts", List.of(productA)))).body()).get("id").asLong();
        assertEquals(200, send("POST", "/api/leads", null, json.writeValueAsString(Map.of(
                "fullName", "Kamran" + run + " Aliyev", "phone", phoneB, "email", "kamran" + run + "@example.org",
                "message", "Axsam zeng edin", "source", "ORDER", "requestedProducts", List.of(productB)))).statusCode());
        demo = login("demo", "demo1234");
        admin = login("admin", "it-only-admin-password-1");
    }

    @Test
    void demoSearchOnPersonalDataMatchesNothing() throws Exception {
        List<Long> nothing = ids(demo, "no-such-thing-" + run);
        assertTrue(nothing.isEmpty());

        // The pairs from the review (a true prefix, then the same with the last character wrong) and more.
        List<String> probes = List.of(
                phoneA, phoneA.substring(0, 9), phoneA.substring(0, 8) + "x", phoneA.substring(3),
                emailA, emailA.substring(0, emailA.indexOf('@') + 3), "aynur" + run + "@ez",
                nameA, "Aynur" + run, "aynur" + run, "Quliyeva", nameA.toUpperCase(),
                messageA, "Qapi kodu", "kodu " + run,
                " " + phoneA + " ", phoneA + "%", "%" + phoneA.substring(4));
        for (String probe : probes) {
            List<Long> found = ids(demo, probe);
            assertFalse(found.contains(idA), "demo search for '" + probe + "' must not single out the lead");
        }
        // Same answer whether the fragment is in the hidden columns or not: nothing to learn from it.
        assertEquals(ids(demo, phoneA.substring(0, 8) + "x"), ids(demo, phoneA.substring(0, 9)));
        assertEquals(ids(demo, "aynur" + run + "@ez"), ids(demo, "aynur" + run + "@ex"));
    }

    @Test
    void demoCanStillSearchWhatItIsShown() throws Exception {
        JsonNode rows = leads(demo, "?search=" + enc(productA.toLowerCase()));
        assertEquals(1, rows.size());
        JsonNode lead = rows.get(0);
        assertEquals(idA, lead.get("id").asLong());
        assertEquals("A***", lead.get("fullName").asText());
        assertEquals("*** ** " + phoneA.substring(phoneA.length() - 2), lead.get("phone").asText());
        assertEquals("***@***", lead.get("email").asText());
        assertFalse(lead.get("message").asText().contains(run));
        assertTrue(lead.get("requestedProducts").asText().contains(productA));

        // Filters on columns that are shown as they are keep working and stay masked.
        String all = send("GET", "/api/leads?source=ORDER&status=NEW&referrer=WEBSITE", demo, null).body();
        assertTrue(all.contains(productA) && all.contains(productB));
        for (String secret : List.of(phoneA, phoneB, emailA, nameA, messageA, "Quliyeva", "Aliyev")) {
            assertFalse(all.contains(secret), "masked answer must not contain " + secret);
        }
    }

    @Test
    void realAdminSearchesPersonalDataAndSeesIt() throws Exception {
        for (String probe : List.of(phoneA.substring(0, 9), emailA.substring(0, 12), "aynur" + run, messageA, productA)) {
            assertEquals(List.of(idA), ids(admin, probe), "admin search for " + probe);
        }
        assertTrue(ids(admin, phoneA.substring(0, 8) + "x").isEmpty());
        JsonNode lead = leads(admin, "?search=" + enc(phoneA)).get(0);
        assertEquals(nameA, lead.get("fullName").asText());
        assertEquals(phoneA, lead.get("phone").asText());
        assertEquals(emailA, lead.get("email").asText());
        assertEquals(messageA, lead.get("message").asText());
    }

    @Test
    void demoCannotWriteAndRealAdminCan() throws Exception {
        assertEquals(403, send("PATCH", "/api/leads/" + idA + "/status?status=CONTACTED", demo, null).statusCode());
        assertEquals(403, send("PATCH", "/api/leads/" + idA + "/contacted?contacted=true", demo, null).statusCode());
        assertEquals(403, send("DELETE", "/api/leads/" + idA + "/soft-delete", demo, null).statusCode());
        assertEquals(403, send("GET", "/api/admin/visitors", demo, null).statusCode());
        assertEquals(401, send("GET", "/api/leads?search=" + enc(phoneA), null, null).statusCode());

        assertEquals(200, send("PATCH", "/api/leads/" + idA + "/contacted?contacted=true", admin, null).statusCode());
        assertTrue(leads(admin, "?search=" + enc(phoneA)).get(0).get("contacted").asBoolean());
    }

    private List<Long> ids(String token, String search) throws Exception {
        List<Long> ids = new ArrayList<>();
        leads(token, "?search=" + enc(search)).forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private JsonNode leads(String token, String query) throws Exception {
        HttpResponse<String> response = send("GET", "/api/leads" + query, token, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String login(String username, String password) throws Exception {
        HttpResponse<String> response = send("POST", "/api/admin/auth/login", null,
                json.writeValueAsString(Map.of("username", username, "password", password)));
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("token").asText();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
