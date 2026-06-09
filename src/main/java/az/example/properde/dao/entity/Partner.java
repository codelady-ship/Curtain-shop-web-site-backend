package az.example.properde.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "partners")
@Getter
@Setter
public class Partner extends BaseEntity {
    @Column(name = "name", nullable = false, length = 180)
    private String name;
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;
    @Column(name = "website_url", length = 1000)
    private String websiteUrl;
    @Column(name = "active")
    private Boolean active = true;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
