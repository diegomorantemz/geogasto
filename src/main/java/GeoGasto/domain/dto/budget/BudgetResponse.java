package GeoGasto.domain.dto.budget;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {

    private Long id;
    private Long categoryId;
    private String categoryNombre;
    private String categoryColor;
    private String mes;
    private BigDecimal monto;
    private BigDecimal gastado;
    private BigDecimal disponible;
    private BigDecimal porcentaje;
    private String estado; // OK | ALERTA | EXCEDIDO
}
