package az.example.properde.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "visitors")
@Getter
@Setter
public class Visitor extends BaseEntity {
    @Column(name = "ip_address", length = 120)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "path", length = 1000)
    private String path;

    @Column(name = "referrer", length = 1000)
    private String referrer;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt = LocalDateTime.now();
}
