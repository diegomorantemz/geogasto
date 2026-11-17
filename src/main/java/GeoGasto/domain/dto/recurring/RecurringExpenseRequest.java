package GeoGasto.domain.dto.recurring;

import GeoGasto.persistence.entity.RecurringFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseRequest {

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "El monto admite hasta 10 enteros y 2 decimales")
    private BigDecimal monto;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @NotNull(message = "La frecuencia es obligatoria")
    private RecurringFrequency frecuencia;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String descripcion;

    private Long categoryId;

    private Long locationId;

    private Boolean activo;
}
