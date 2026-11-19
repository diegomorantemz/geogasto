package GeoGasto.domain.dto.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralBudgetRequest {

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El presupuesto debe ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "El presupuesto admite hasta 10 enteros y 2 decimales")
    private BigDecimal monto;
}
