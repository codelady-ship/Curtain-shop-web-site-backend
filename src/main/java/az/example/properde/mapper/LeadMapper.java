package az.example.properde.mapper;

import az.example.properde.dao.dto.lead.LeadResponseDTO;
import az.example.properde.dao.entity.Lead;
import az.example.properde.dao.enums.LeadSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeadMapper {

    public LeadResponseDTO toDto(Lead lead) {
        if (lead == null) {
            return null;
        }

        String fullName = lead.getFullName();
        return LeadResponseDTO.builder()
                .id(lead.getId())
                .fullName(fullName)
                .name(fullName)
                .phone(lead.getPhone())
                .email(lead.getEmail())
                .message(lead.getMessage())
                .referrer(lead.getReferrer())
                .source(lead.getSource() == null ? null : LeadSource.fromString(lead.getSource()).canonical().name())
                .status(lead.getStatus())
                .contacted(lead.getContacted())
                .promoCode(lead.getPromoCode())
                .requestedProducts(lead.getRequestedProducts())
                .likedProductsSummary(lead.getLikedProductsSummary())
                .likedProductLinks(lead.getLikedProductLinks())
                .totalAmount(lead.getTotalAmount())
                .visualizationImageUrl(lead.getVisualizationImageUrl())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }

    public List<LeadResponseDTO> toDtoList(List<Lead> leads) {
        if (leads == null) {
            return List.of();
        }
        return leads.stream().map(this::toDto).toList();
    }
}
