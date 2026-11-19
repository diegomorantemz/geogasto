package GeoGasto.domain.dto.expense;

import jakarta.validation.constraints.DecimalMax;
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
public class ExpenseWithLocationRequest {

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "El monto admite hasta 10 enteros y 2 decimales")
    private BigDecimal monto;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String descripcion;

    private Long categoryId;

    // Datos de la ubicación (opcionales)
    @Size(max = 500, message = "La dirección no puede superar 500 caracteres")
    private String locationDireccion;

    @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
    private Double locationLatitud;

    @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
    private Double locationLongitud;

    @Size(max = 100, message = "La ciudad no puede superar 100 caracteres")
    private String locationCiudad;

    @Size(max = 100, message = "El país no puede superar 100 caracteres")
    private String locationPais;

    @Size(max = 255, message = "El placeId no puede superar 255 caracteres")
    private String locationPlaceId;
}
