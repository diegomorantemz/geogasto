package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByUserId(Long userId, Pageable pageable);

    List<Expense> findByUserIdAndFechaBetween(Long userId, LocalDate desde, LocalDate hasta);
}