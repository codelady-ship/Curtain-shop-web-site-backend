package az.example.properde.repository;

import az.example.properde.dao.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {
    List<Visitor> findByDeletedFalseOrderByVisitedAtDesc();
}
