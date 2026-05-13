package az.example.properde.dao.dto.product;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ColorDto {

    @JsonAlias({"name"})
    private String colorName;

    @JsonAlias({"code", "hex"})
    private String colorCode;

    @JsonAlias({"colorHex"})
    private String colorHex;

    @JsonAlias({"image", "preview"})
    private String mainImage;

    @JsonAlias({"imageUrl"})
    private String imageUrl;

    public String resolvedColorHex() {
        if (StringUtils.hasText(colorHex)) {
            return colorHex.trim();
        }
        if (StringUtils.hasText(colorCode)) {
            return colorCode.trim();
        }
        return "#cccccc";
    }

    public String resolvedImageUrl() {
        if (StringUtils.hasText(imageUrl)) {
            return imageUrl.trim();
        }
        if (StringUtils.hasText(mainImage)) {
            return mainImage.trim();
        }
        return "";
    }
}
