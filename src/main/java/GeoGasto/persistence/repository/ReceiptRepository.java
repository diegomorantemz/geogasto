package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByExpenseId(Long expenseId);

    Optional<Receipt> findByExpenseIdAndUserId(Long expenseId, Long userId);

    boolean existsByExpenseId(Long expenseId);
}
