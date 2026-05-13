package az.example.properde.repository;

import az.example.properde.dao.entity.Product;
import az.example.properde.dao.enums.CategoryType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Məhsulları filtr etmək üçün Specification metodu
    static Specification<Product> filterProducts(String search, CategoryType category, String room, Boolean inDiscount) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));  // Yalnız silinməyən məhsulları seçirik

            // Axtarış sözü varsa, məhsulun adında axtarış edirik
            if (StringUtils.hasText(search)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }

            // Kateqoriya varsa, filter əlavə edirik
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            // Otaq varsa, filter əlavə edirik
            if (room != null && !room.isEmpty()) {
                predicates.add(cb.equal(root.get("room"), room));
            }

            // Endirimli məhsullar varsa, filter əlavə edirik
            if (inDiscount != null) {
                predicates.add(cb.equal(root.get("isDiscount"), inDiscount));  // Endirimli məhsulları filtrləyirik
            }

            // Nəticəni qaytarırıq
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
