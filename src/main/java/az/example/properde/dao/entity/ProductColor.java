package az.example.properde.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_colors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String colorName;

    @Column(name = "color_hex")
    private String colorHex;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @JsonIgnore
    private Product product;

    public String getMainImage() {
        return imageUrl;
    }

    public void setMainImage(String mainImage) {
        this.imageUrl = mainImage;
    }

    public String getColorCode() {
        return colorHex;
    }

    public void setColorCode(String colorCode) {
        this.colorHex = colorCode;
    }
}
