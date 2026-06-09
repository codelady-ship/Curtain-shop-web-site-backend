package az.example.properde.repository;

import az.example.properde.dao.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    List<Partner> findByDeletedFalseOrderBySortOrderAscCreatedAtDesc();
    List<Partner> findByDeletedFalseAndActiveTrueOrderBySortOrderAscCreatedAtDesc();
}
