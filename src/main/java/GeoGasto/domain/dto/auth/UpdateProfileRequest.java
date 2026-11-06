package GeoGasto.domain.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe ser un código ISO de 3 letras")
    private String moneda;

    @Size(max = 50, message = "La zona horaria no puede superar 50 caracteres")
    private String timezone;
}
