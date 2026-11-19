package GeoGasto.domain.dto.income;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeResponse {

    private Long id;
    private BigDecimal monto;
    private LocalDate fecha;
    private String descripcion;
    private String fuente;
    private LocalDateTime createdAt;
}
