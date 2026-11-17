package GeoGasto.domain.dto.budget;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryResponse {

    private String mes;
    private BigDecimal totalPresupuesto;
    private BigDecimal totalGastado;
    private BigDecimal totalDisponible;
    private BigDecimal porcentaje;
    private List<BudgetResponse> presupuestos;
}
