package az.example.properde.dao.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor 
@AllArgsConstructor
@Table(name = "analytics")
public class Analytics extends BaseEntity {
    private LocalDate visitDate;
    private Long visitCount;
}