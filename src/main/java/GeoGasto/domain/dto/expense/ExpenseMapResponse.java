package GeoGasto.domain.dto.expense;

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
public class ExpenseMapResponse {
    private Long id;
    private BigDecimal monto;
    private LocalDate fecha;
    private String descripcion;
    private String categoryNombre;
    private String categoryColor;
    private Long locationId;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String ciudad;
    private String pais;
}
