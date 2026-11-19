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
    private BigDecimal presupuestoGeneral;
    private BigDecimal gastadoGeneral;
    private BigDecimal disponibleGeneral;
    private BigDecimal porcentajeGeneral;
    private String estadoGeneral;
    private List<BudgetResponse> presupuestos;
}
