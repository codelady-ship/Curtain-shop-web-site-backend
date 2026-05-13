package az.example.properde.dao.dto.lead;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadResponseDTO {
    private Long id;
    private String fullName;
    private String name;
    private String phone;
    private String email;
    private String message;
    private String referrer;
    private String source;
    private String status;
    private Boolean contacted;
    private String promoCode;
    private String requestedProducts;
    private String likedProductsSummary;
    private String likedProductLinks;
    private Double totalAmount;
    private String visualizationImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
