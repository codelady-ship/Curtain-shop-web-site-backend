package az.example.properde.dao.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartnerDto {
    private Long id;
    private String name;
    private String logoUrl;
    private String websiteUrl;
    private Boolean active;
    private Integer sortOrder;
}
