package az.example.properde.dao.dto.product;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SizeDto {

    @JsonAlias({"size", "value"})
    private String sizeValue;

    private Double price;

    private Double oldPrice;
}
