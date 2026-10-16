package GeoGasto.domain.dto.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {
    private Long id;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String ciudad;
    private String pais;
    private String placeId;
    private LocalDateTime createdAt;
}