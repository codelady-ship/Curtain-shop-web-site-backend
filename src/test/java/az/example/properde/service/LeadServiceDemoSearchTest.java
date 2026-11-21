package az.example.properde.service;

import az.example.properde.dao.dto.lead.LeadResponseDTO;
import az.example.properde.mapper.LeadMapper;
import az.example.properde.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The search of the demo admin must not be an oracle on masked columns: a query that filters on name,
 * phone, e-mail or message tells the caller which rows contain a substring, and with that the full
 * value, however well the answer is masked afterwards.
 */
class LeadServiceDemoSearchTest {
    /** Lead columns that hold personal data of a customer, in every spelling the reader knows. */
    private static final List<String> PERSONAL = List.of(
            "full_name", "name", "customer_name", "fullname", "phone", "email", "message");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LeadService service = new LeadService(
            mock(LeadRepository.class), mock(LeadMapper.class), mock(FileStorageService.class), jdbc);

    @BeforeEach
    void leadsTable() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                "id", "full_name", "name", "customer_name", "fullname", "phone", "email", "message", "referrer",
                "source", "status", "contacted", "promo_code", "requested_products", "liked_products_summary",
                "liked_product_links", "total_amount", "visualization_image_url", "created_at", "updated_at", "deleted"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("full_name", "Aynur Quliyeva");
        row.put("phone", "994501112233");
        row.put("email", "aynur@example.com");
        row.put("message", "Qapi kodu 4471");
        row.put("source", "ORDER");
        row.put("status", "NEW");
        row.put("promo_code", "YAZ10");
        row.put("requested_products", "Blackout perde");
        row.put("visualization_image_url", "/uploads/room.jpg");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
    }

    private String sqlOf(Runnable call) {
        call.run();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sql.capture(), any(Object[].class));
        return sql.getValue().toLowerCase();
    }

    private static String where(String sql) {
        int from = sql.indexOf(" where ");
        int order = sql.indexOf(" order by ");
        return sql.substring(from, order < 0 ? sql.length() : order);
    }

    @Test
    void demoSearchNeverFiltersOnPersonalColumns() {
        String where = where(sqlOf(() -> service.getAllLeadsForDemo("ALL", null, null, "9945011")));

        for (String column : PERSONAL) {
            assertFalse(where.contains("cast(" + column + " "), "demo search must not look at " + column + ": " + where);
        }
        for (String column : LeadDemoView.SEARCH_COLUMNS) {
            assertTrue(where.contains("cast(" + column + " "), "demo search still covers " + column);
        }
    }

    @Test
    void demoSortAndFiltersDoNotUsePersonalColumnsEither() {
        String sql = sqlOf(() -> service.getAllLeadsForDemo("ORDER", "NEW", "INSTAGRAM", "aynur@ex"));
        String afterSelect = sql.substring(sql.indexOf(" where "));

        for (String column : PERSONAL) {
            assertFalse(afterSelect.matches(".*[ (,]" + column + "[ ),].*"),
                    "no filter or sort on " + column + ": " + afterSelect);
        }
    }

    @Test
    void demoSearchColumnsAreAllReturnedUnmasked() {
        // The rule that keeps the search safe: it may only look at what the demo admin can read anyway.
        for (String column : LeadDemoView.SEARCH_COLUMNS) {
            assertFalse(PERSONAL.contains(column), column + " is personal and must not be searchable in the demo");
        }
        assertEquals(List.of("promo_code", "requested_products", "liked_products_summary", "liked_product_links"),
                LeadDemoView.SEARCH_COLUMNS);
    }

    @Test
    void demoRowsComeBackMasked() {
        LeadResponseDTO lead = service.getAllLeadsForDemo("ALL", null, null, null).get(0);

        assertEquals("A***", lead.getFullName());
        assertEquals("A***", lead.getName());
        assertEquals("*** ** 33", lead.getPhone());
        assertEquals("***@***", lead.getEmail());
        assertEquals("(demo: gizlədilib)", lead.getMessage());
        assertNull(lead.getVisualizationImageUrl());
        assertEquals("YAZ10", lead.getPromoCode());
        assertEquals("Blackout perde", lead.getRequestedProducts());
    }

    @Test
    void realAdminStillSearchesEveryColumnAndSeesEverything() {
        String where = where(sqlOf(() -> service.getAllLeads("ALL", null, null, "9945011")));
        for (String column : PERSONAL) {
            assertTrue(where.contains("cast(" + column + " "), "admin search covers " + column);
        }
        for (String column : LeadDemoView.SEARCH_COLUMNS) {
            assertTrue(where.contains("cast(" + column + " "), "admin search covers " + column);
        }

        LeadResponseDTO lead = service.getAllLeads("ALL", null, null, null).get(0);
        assertEquals("Aynur Quliyeva", lead.getFullName());
        assertEquals("994501112233", lead.getPhone());
        assertEquals("aynur@example.com", lead.getEmail());
        assertEquals("Qapi kodu 4471", lead.getMessage());
        assertEquals("/uploads/room.jpg", lead.getVisualizationImageUrl());
    }
}
