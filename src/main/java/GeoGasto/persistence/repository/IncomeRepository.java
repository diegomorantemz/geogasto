package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long>, JpaSpecificationExecutor<Income> {

    Page<Income> findByUserId(Long userId, Pageable pageable);

    List<Income> findByUserIdAndFechaBetween(Long userId, LocalDate desde, LocalDate hasta);

    @Query("""
            select coalesce(sum(i.monto), 0)
            from Income i
            where i.user.id = :userId
              and i.fecha between :desde and :hasta
            """)
    BigDecimal sumarEntreFechas(@Param("userId") Long userId,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta);
}
