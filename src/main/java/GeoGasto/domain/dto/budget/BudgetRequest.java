package GeoGasto.domain.dto.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRequest {

    @NotNull(message = "La categoría es obligatoria")
    private Long categoryId;

    @NotNull(message = "El mes es obligatorio")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
    private String mes;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El presupuesto debe ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "El presupuesto admite hasta 10 enteros y 2 decimales")
    private BigDecimal monto;
}
