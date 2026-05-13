package az.example.properde.dao.dto.product;

import az.example.properde.dao.enums.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {

    private Long id;
    private String name;
    private String room;
    private String partType;
    private Double rating;
    private Boolean isPopular;
    private Boolean isDiscount;
    private String description;
    private CategoryType category;
    private List<ColorDto> colors;
    private List<SizeDto> sizeOptions;

    // Convenience fields for admin/public cards that expect flattened product data.
    private String imageUrl;
    private Double price;
    private Double oldPrice;
    private String sizeValue;
    private String colorName;
    private String colorCode;
    private String colorHex;
}
