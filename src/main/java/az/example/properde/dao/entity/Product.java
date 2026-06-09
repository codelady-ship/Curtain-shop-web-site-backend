package az.example.properde.dao.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
public class Product extends BaseEntity {

    @NotBlank(message = "Modelin adı boş ola bilməz.")
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "part_type", length = 255)
    private String partType;

    @NotBlank(message = "Model haqqında məlumat boş ola bilməz.")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "room", length = 100)
    private String room;

    @Min(0)
    @Max(5)
    private Double rating;

    @Column(name = "is_popular")
    private Boolean isPopular;

    @Column(name = "is_discount")
    private Boolean isDiscount;

    @Column(name = "category", length = 255)
    @NotBlank(message = "Kateqoriya seçilməlidir.")
    private String category;

    @Column(name = "status", length = 255)
    private String status;

    @Column(name = "name_az", length = 255)
    private String nameAz;

    @Column(name = "name_ru", length = 255)
    private String nameRu;

    @Column(name = "name_en", length = 255)
    private String nameEn;

    @Column(name = "description_az", columnDefinition = "TEXT")
    private String descriptionAz;

    @Column(name = "description_ru", columnDefinition = "TEXT")
    private String descriptionRu;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "room_type", length = 120)
    private String roomType;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSize> sizeOptions = new ArrayList<>();
}
