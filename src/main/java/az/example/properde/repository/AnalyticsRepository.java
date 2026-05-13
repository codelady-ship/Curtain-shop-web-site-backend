package az.example.properde.repository;

import az.example.properde.dao.entity.Analytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AnalyticsRepository extends JpaRepository<Analytics, Long> {
    Optional<Analytics> findByVisitDate(LocalDate visitDate);
    List<Analytics> findAllByVisitDateAfter(LocalDate date);
    List<Analytics> findAllByVisitDateBetween(LocalDate start, LocalDate end);
}
