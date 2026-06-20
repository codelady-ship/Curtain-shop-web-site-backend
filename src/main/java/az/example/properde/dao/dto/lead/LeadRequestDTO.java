package az.example.properde.dao.dto.lead;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class LeadRequestDTO {

    @JsonAlias({"name", "customerName"})
    @Size(max = 100, message = "Ad maksimum 100 simvol ola bilər")
    private String fullName;

    @Pattern( regexp = "^[+]?\\d{9,15}$",
            message = "Düzgün nömrə daxil edin")
    private String phone;

    private String email;
    private String message;

    /**
     * Keep this as String instead of LeadSource so JSON and multipart form-data
     * can send business aliases such as PROMO, VISUALIZATION, MEASUREMENT,
     * WISHLIST, FAVORITES, ORDERS without Spring's enum converter rejecting
     * the request before LeadSource.fromString() can normalize it.
     */
    @JsonAlias({"leadSource", "type", "formType"})
    private String source;

    private String referrer;

    @JsonAlias({"products", "cartProducts", "selectedProducts", "requestedProductNames"})
    private List<String> requestedProducts;

    @JsonAlias({"likedProductNames", "favorites", "favoriteProducts", "wishlist", "wishlistProducts"})
    private List<String> likedProducts;

    @JsonAlias({"favoriteProductLinks", "wishlistProductLinks"})
    private List<String> likedProductLinks;

    @JsonAlias({"amount", "total", "totalPrice"})
    private Double totalAmount;

    @JsonAlias({"promo", "promo_code", "discountCode"})
    private String promoCode;
}
