package az.example.properde.dao.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BannerDto {
    private Long id;
    private String titleAz;
    private String titleRu;
    private String titleEn;
    private String descriptionAz;
    private String descriptionRu;
    private String descriptionEn;
    private String buttonTextAz;
    private String buttonTextRu;
    private String buttonTextEn;
    private String linkUrl;
    private String desktopImageUrl;
    private String mobileImageUrl;
    private String placement;
    private String visualType;
    private Boolean active;
    private Integer sortOrder;
}
