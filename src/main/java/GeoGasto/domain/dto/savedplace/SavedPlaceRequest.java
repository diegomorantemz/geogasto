package GeoGasto.domain.dto.savedplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPlaceRequest {

    @NotBlank(message = "El alias es obligatorio")
    @Size(max = 50, message = "El alias no puede superar 50 caracteres")
    private String alias;

    @NotNull(message = "La ubicación es obligatoria")
    private Long locationId;
}
