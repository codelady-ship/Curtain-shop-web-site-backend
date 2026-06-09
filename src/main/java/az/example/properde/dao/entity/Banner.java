package az.example.properde.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "banners")
@Getter
@Setter
public class Banner extends BaseEntity {
    @Column(name = "title_az", length = 255)
    private String titleAz;
    @Column(name = "title_ru", length = 255)
    private String titleRu;
    @Column(name = "title_en", length = 255)
    private String titleEn;

    @Column(name = "description_az", columnDefinition = "TEXT")
    private String descriptionAz;
    @Column(name = "description_ru", columnDefinition = "TEXT")
    private String descriptionRu;
    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "button_text_az", length = 120)
    private String buttonTextAz;
    @Column(name = "button_text_ru", length = 120)
    private String buttonTextRu;
    @Column(name = "button_text_en", length = 120)
    private String buttonTextEn;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;
    @Column(name = "desktop_image_url", length = 1000)
    private String desktopImageUrl;
    @Column(name = "mobile_image_url", length = 1000)
    private String mobileImageUrl;
    @Column(name = "placement", length = 40)
    private String placement = "MAIN";
    @Column(name = "visual_type", length = 40)
    private String visualType = "IMAGE";
    @Column(name = "active")
    private Boolean active = true;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
