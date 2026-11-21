package az.example.properde.service;

import az.example.properde.dao.dto.lead.LeadResponseDTO;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * What the public read-only demo admin may learn about a lead: the table without real customers'
 * contact details. Used only through {@link LeadService#getAllLeadsForDemo}.
 */
public final class LeadDemoView {
    private LeadDemoView() {
    }

    /**
     * The only lead columns a demo search may look at. Every one of them is returned unmasked by
     * {@link #mask}, so matching on them tells the demo admin nothing it cannot already read.
     * Never add a column that {@link #mask} hides (name, phone, e-mail, message): searching a hidden
     * column reveals its value through which rows match.
     */
    public static final List<String> SEARCH_COLUMNS = List.of(
            "promo_code", "requested_products", "liked_products_summary", "liked_product_links");

    /** Copy of a lead with personal data hidden. */
    public static LeadResponseDTO mask(LeadResponseDTO lead) {
        return LeadResponseDTO.builder()
                .id(lead.getId())
                .fullName(maskName(lead.getFullName()))
                .name(maskName(lead.getName()))
                .phone(maskPhone(lead.getPhone()))
                .email(StringUtils.hasText(lead.getEmail()) ? "***@***" : lead.getEmail())
                .message(StringUtils.hasText(lead.getMessage()) ? "(demo: gizlədilib)" : lead.getMessage())
                .referrer(lead.getReferrer())
                .source(lead.getSource())
                .status(lead.getStatus())
                .contacted(lead.getContacted())
                .promoCode(lead.getPromoCode())
                .requestedProducts(lead.getRequestedProducts())
                .likedProductsSummary(lead.getLikedProductsSummary())
                .likedProductLinks(lead.getLikedProductLinks())
                .totalAmount(lead.getTotalAmount())
                .visualizationImageUrl(null)
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .deleted(lead.getDeleted())
                .build();
    }

    private static String maskName(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1) + "***";
    }

    private static String maskPhone(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() <= 2 ? "***" : "*** ** " + digits.substring(digits.length() - 2);
    }
}
