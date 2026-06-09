package az.example.properde.dao.dto.product;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotBlank(message = "Ad boş ola bilməz")
    private String name;

    private String room;

    @Min(0)
    @Max(5)
    private Double rating;

    private Boolean isPopular;

    private Boolean isDiscount;

    @NotBlank(message = "Açıqlama boş ola bilməz")
    private String description;

    private String imageUrl;

    @JsonAlias({"fabric"})
    private String partType;

    @NotBlank(message = "Kateqoriya seçilməlidir")
    private String category;

    private String status;

    private String nameAz;
    private String nameRu;
    private String nameEn;
    private String descriptionAz;
    private String descriptionRu;
    private String descriptionEn;
    private String roomType;
    private Integer discountPercent;
    private Integer sortOrder;

    private List<ColorDto> colors;

    private List<SizeDto> sizeOptions;
}
