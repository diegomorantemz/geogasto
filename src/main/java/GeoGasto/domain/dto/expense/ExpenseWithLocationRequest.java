package GeoGasto.domain.dto.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class ExpenseWithLocationRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monto;

    @NotNull
    private LocalDate fecha;

    private String descripcion;

    private Long categoryId;

    // Datos de la ubicación (opcionales)
    private String locationDireccion;
    private Double locationLatitud;
    private Double locationLongitud;
    private String locationCiudad;
    private String locationPais;
    private String locationPlaceId;
}