package GeoGasto.domain.dto.geocode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResult {
    private String displayName;
    private Double latitud;
    private Double longitud;
    private String ciudad;
    private String pais;
    private String placeId;
}