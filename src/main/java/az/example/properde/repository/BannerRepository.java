package az.example.properde.repository;

import az.example.properde.dao.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByDeletedFalseOrderBySortOrderAscCreatedAtDesc();
    List<Banner> findByDeletedFalseAndActiveTrueOrderBySortOrderAscCreatedAtDesc();
}
