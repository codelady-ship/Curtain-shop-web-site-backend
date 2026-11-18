package az.example.properde.controller;

import az.example.properde.dao.dto.lead.LeadResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The read-only demo admin sees the leads table without real customers' contact details. */
class LeadDemoMaskTest {
    @Test
    void personalDataIsMasked() {
        LeadResponseDTO lead = LeadResponseDTO.builder()
                .id(7L).fullName("Aysel Məmmədova").name("Aysel").phone("+994 50 123 45 67")
                .email("aysel@example.com").message("Zəng edin").source("ORDER").status("NEW")
                .totalAmount(120.0).visualizationImageUrl("/uploads/room.jpg").build();

        LeadResponseDTO masked = LeadController.maskForDemo(lead);

        assertEquals(7L, masked.getId());
        assertEquals("A***", masked.getFullName());
        assertEquals("*** ** 67", masked.getPhone());
        assertEquals("***@***", masked.getEmail());
        assertEquals("(demo: gizlədilib)", masked.getMessage());
        assertNull(masked.getVisualizationImageUrl());
        assertEquals("ORDER", masked.getSource());
        assertEquals(120.0, masked.getTotalAmount());
        assertEquals("Aysel Məmmədova", lead.getFullName(), "original stays untouched");
    }
}
