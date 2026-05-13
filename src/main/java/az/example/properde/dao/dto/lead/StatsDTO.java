package az.example.properde.dao.dto.lead;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatsDTO {
  private String id;
    private String label;
    private Long value;
    private String trend;
}
