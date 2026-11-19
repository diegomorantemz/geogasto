package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.GeneralBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface GeneralBudgetRepository extends JpaRepository<GeneralBudget, Long> {

    Optional<GeneralBudget> findByUserIdAndPeriodo(Long userId, LocalDate periodo);

    Optional<GeneralBudget> findByIdAndUserId(Long id, Long userId);
}
