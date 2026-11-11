package az.example.properde.repository;

import az.example.properde.dao.entity.Admin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsernameIgnoreCase(String username);

    /** Row lock (SELECT ... FOR UPDATE) so concurrent reset requests/attempts are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where lower(a.username) = lower(:username)")
    Optional<Admin> findByUsernameForUpdate(@Param("username") String username);
}
