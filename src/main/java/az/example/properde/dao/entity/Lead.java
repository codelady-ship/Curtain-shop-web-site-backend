package az.example.properde.dao.entity;

import az.example.properde.dao.enums.LeadSource;
import az.example.properde.dao.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    private String phone;
    private String email;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String referrer;

    /**
     * Stored as a String intentionally. This prevents Hibernate enum hydration
     * failures when old rows contain unsupported source values like PROMO,
     * VISUALIZATION, MEASUREMENT, WISHLIST, etc. Service/prePersist always
     * writes the DB-safe enum names: ALL, ORDER, VISUAL, DISCOUNT, MEASURE, HEART.
     */
    @Column(name = "source", length = 50)
    private String source;

    /**
     * Stored as a String to keep old rows readable even if they contain previous labels
     * such as "YENİ". Service-level normalization writes canonical LeadStatus names.
     */
    private String status;

    private Boolean contacted;

    @Column(name = "promo_code")
    private String promoCode;

    @Column(name = "requested_products", columnDefinition = "TEXT")
    private String requestedProducts;

    @Column(name = "liked_products_summary", columnDefinition = "TEXT")
    private String likedProductsSummary;

    @Column(name = "liked_product_links", columnDefinition = "TEXT")
    private String likedProductLinks;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "visualization_image_url")
    private String visualizationImageUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        applyDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        applyDefaults();
    }

    @PostLoad
    public void postLoad() {
        applyDefaults();
    }

    private void applyDefaults() {
        if (!StringUtils.hasText(source)) {
            source = LeadSource.ALL.name();
        } else {
            source = LeadSource.fromString(source).canonical().name();
        }
        if (!StringUtils.hasText(status)) {
            status = LeadStatus.NEW.name();
        } else {
            status = LeadStatus.fromString(status).canonicalName();
        }
        if (contacted == null) {
            contacted = false;
        }
        if (!StringUtils.hasText(referrer)) {
            referrer = "WEBSITE";
        }
    }
}
