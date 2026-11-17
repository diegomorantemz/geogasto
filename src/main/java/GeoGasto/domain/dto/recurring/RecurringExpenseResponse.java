package GeoGasto.domain.dto.recurring;

import GeoGasto.persistence.entity.RecurringFrequency;
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
public class RecurringExpenseResponse {

    private Long id;
    private BigDecimal monto;
    private String descripcion;
    private RecurringFrequency frecuencia;
    private LocalDate fechaInicio;
    private LocalDate proximaFecha;
    private LocalDate fechaFin;
    private Boolean activo;
    private Long categoryId;
    private String categoryNombre;
    private String categoryColor;
    private Long locationId;
    private String locationDireccion;
    private LocalDateTime createdAt;
}
