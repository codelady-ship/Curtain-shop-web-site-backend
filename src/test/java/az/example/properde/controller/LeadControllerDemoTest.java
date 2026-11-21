package az.example.properde.controller;

import az.example.properde.dao.dto.lead.LeadResponseDTO;
import az.example.properde.security.AdminAuthInterceptor;
import az.example.properde.security.JwtTokenService;
import az.example.properde.service.LeadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/leads: a demo token only ever reaches the demo read path, a real admin the full one. */
class LeadControllerDemoTest {
    private final JwtTokenService jwt = new JwtTokenService("unit-test-secret-0123456789-abcdefghij", 3600);
    private final LeadService leads = mock(LeadService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new LeadController(leads, new ObjectMapper()))
            .addInterceptors(new AdminAuthInterceptor(jwt))
            .build();

    private final String demo = "Bearer " + jwt.issue(2L, "demo", JwtTokenService.ROLE_DEMO);
    private final String admin = "Bearer " + jwt.issue(1L, "admin", JwtTokenService.ROLE_ADMIN);

    @BeforeEach
    void rows() {
        when(leads.getAllLeads(any(), any(), any(), any())).thenReturn(List.of(
                LeadResponseDTO.builder().id(1L).fullName("Aynur Quliyeva").phone("994501112233").build()));
        when(leads.getAllLeadsForDemo(any(), any(), any(), any())).thenReturn(List.of(
                LeadResponseDTO.builder().id(1L).fullName("A***").phone("*** ** 33").build()));
    }

    @Test
    void demoTokenNeverReachesTheUnmaskedSearch() throws Exception {
        for (String url : List.of(
                "/api/leads", "/api/leads?search=9945011", "/api/leads?search=aynur@ex",
                "/api/leads?source=ORDER&status=NEW&referrer=INSTAGRAM&search=Aynur",
                "/api/leads?search=9945011&search=aynur")) {
            mvc.perform(get(url).header("Authorization", demo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].fullName").value("A***"))
                    .andExpect(jsonPath("$[0].phone").value("*** ** 33"));
        }
        verify(leads, never()).getAllLeads(any(), any(), any(), any());
        verify(leads).getAllLeadsForDemo("ORDER", "NEW", "INSTAGRAM", "Aynur");
    }

    @Test
    void realAdminSearchesAndSeesFullData() throws Exception {
        mvc.perform(get("/api/leads?search=9945011").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Aynur Quliyeva"))
                .andExpect(jsonPath("$[0].phone").value("994501112233"));
        verify(leads).getAllLeads("ALL", null, null, "9945011");
        verify(leads, never()).getAllLeadsForDemo(any(), any(), any(), any());
    }

    @Test
    void noTokenIsRefused() throws Exception {
        mvc.perform(get("/api/leads?search=9945011")).andExpect(status().isUnauthorized());
    }
}
