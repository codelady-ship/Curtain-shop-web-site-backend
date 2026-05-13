package az.example.properde.dao.entity;

import az.example.properde.dao.enums.CategoryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String name;

    @Column(name = "part_type")
    private String partType;

    @NotBlank(message = "Model haqqında məlumat boş ola bilməz.")
    private String description;

    private String room;

    @Min(0)
    @Max(5)
    private Double rating;

    @Column(name = "is_popular")
    private Boolean isPopular;

    @Column(name = "is_discount")
    private Boolean isDiscount;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Kateqoriya seçilməlidir.")
    private CategoryType category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSize> sizeOptions = new ArrayList<>();
}
