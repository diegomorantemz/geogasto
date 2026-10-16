package GeoGasto.domain.dto.savedplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPlaceResponse {
    private Long id;
    private String alias;
    private Long locationId;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String ciudad;
    private String pais;
    private String placeId;
    private Integer usageCount;
}