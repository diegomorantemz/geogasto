package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.RecurringExpense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    @EntityGraph(attributePaths = {"category", "location"})
    List<RecurringExpense> findByUserIdOrderByProximaFechaAsc(Long userId);

    @EntityGraph(attributePaths = {"category", "location"})
    Optional<RecurringExpense> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"user", "category", "location"})
    List<RecurringExpense> findByActivoTrue();

    boolean existsByLocationId(Long locationId);
}
