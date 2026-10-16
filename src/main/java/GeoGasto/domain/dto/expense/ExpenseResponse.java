package GeoGasto.domain.dto.expense;

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
public class ExpenseResponse {

    private Long id;
    private BigDecimal monto;
    private LocalDate fecha;
    private String descripcion;

    private Long categoryId;
    private String categoryNombre;

    private Long locationId;
    private String locationDireccion;

    private LocalDateTime createdAt;
}