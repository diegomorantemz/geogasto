package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Budget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @EntityGraph(attributePaths = "category")
    List<Budget> findByUserIdAndPeriodo(Long userId, LocalDate periodo);

    @EntityGraph(attributePaths = "category")
    Optional<Budget> findByUserIdAndCategoryIdAndPeriodo(Long userId, Long categoryId, LocalDate periodo);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);
}
