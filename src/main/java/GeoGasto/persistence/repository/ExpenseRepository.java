package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Page<Expense> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "location"})
    List<Expense> findByUserIdAndFechaBetween(Long userId, LocalDate desde, LocalDate hasta);

    boolean existsByLocationId(Long locationId);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select e.category.id as categoriaId, sum(e.monto) as total
            from Expense e
            where e.user.id = :userId
              and e.fecha between :desde and :hasta
              and e.category is not null
            group by e.category.id
            """)
    List<CategoriaTotal> sumarPorCategoria(@Param("userId") Long userId,
                                           @Param("desde") LocalDate desde,
                                           @Param("hasta") LocalDate hasta);

    @Query("""
            select coalesce(sum(e.monto), 0)
            from Expense e
            where e.user.id = :userId
              and e.category.id = :categoryId
              and e.fecha between :desde and :hasta
            """)
    BigDecimal sumarPorCategoriaYPeriodo(@Param("userId") Long userId,
                                         @Param("categoryId") Long categoryId,
                                         @Param("desde") LocalDate desde,
                                         @Param("hasta") LocalDate hasta);

    interface CategoriaTotal {
        Long getCategoriaId();

        BigDecimal getTotal();
    }
}
