package az.example.properde.dao.dto.product;

import az.example.properde.dao.enums.CategoryType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Kateqoriya seçilməlidir")
    private CategoryType category;

    private List<ColorDto> colors;

    private List<SizeDto> sizeOptions;
}
