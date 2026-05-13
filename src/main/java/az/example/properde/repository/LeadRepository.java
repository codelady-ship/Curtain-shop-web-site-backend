package az.example.properde.repository;

import az.example.properde.dao.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findAllBySourceOrderByCreatedAtDesc(String source);

    long countBySource(String source);

    long countByStatus(String status);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(l.totalAmount), 0)
            FROM Lead l
            WHERE (:source IS NULL OR l.source = :source)
            """)
    Double sumTotalAmountBySource(@Param("source") String source);

    @Query("""
            SELECT l
            FROM Lead l
            WHERE (:source IS NULL OR l.source = :source)
              AND (:status IS NULL OR LOWER(l.status) = LOWER(:status))
              AND (:referrer IS NULL OR LOWER(l.referrer) = LOWER(:referrer))
              AND (
                    :search IS NULL OR :search = '' OR
                    LOWER(COALESCE(l.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(l.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(l.message, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(l.promoCode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY l.createdAt DESC
            """)
    List<Lead> searchLeads(
            @Param("source") String source,
            @Param("status") String status,
            @Param("referrer") String referrer,
            @Param("search") String search
    );

    @Query("SELECT l.source, COUNT(l) FROM Lead l GROUP BY l.source")
    List<Object[]> countGroupedBySource();

    @Query("SELECT l.status, COUNT(l) FROM Lead l GROUP BY l.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT COALESCE(l.referrer, 'UNKNOWN'), COUNT(l) FROM Lead l GROUP BY l.referrer")
    List<Object[]> countGroupedByReferrer();
}
