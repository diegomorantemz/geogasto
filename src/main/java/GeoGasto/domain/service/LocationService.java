package GeoGasto.domain.service;

import GeoGasto.domain.dto.location.LocationRequest;
import GeoGasto.domain.dto.location.LocationResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Location;
import GeoGasto.persistence.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public LocationResponse obtener(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));
        return toResponse(location);
    }

    @Transactional
    public LocationResponse crear(LocationRequest request) {
        Location location = Location.builder()
                .direccion(request.getDireccion())
                .latitud(request.getLatitud())
                .longitud(request.getLongitud())
                .ciudad(request.getCiudad())
                .pais(request.getPais())
                .placeId(request.getPlaceId())
                .build();
        return toResponse(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse actualizar(Long id, LocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));

        location.setDireccion(request.getDireccion());
        location.setLatitud(request.getLatitud());
        location.setLongitud(request.getLongitud());
        location.setCiudad(request.getCiudad());
        location.setPais(request.getPais());
        location.setPlaceId(request.getPlaceId());

        return toResponse(locationRepository.save(location));
    }

    public LocationResponse toResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .direccion(location.getDireccion())
                .latitud(location.getLatitud())
                .longitud(location.getLongitud())
                .ciudad(location.getCiudad())
                .pais(location.getPais())
                .placeId(location.getPlaceId())
                .createdAt(location.getCreatedAt())
                .build();
    }
}