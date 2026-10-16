package GeoGasto.domain.service;

import GeoGasto.domain.dto.savedplace.SavedPlaceResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Location;
import GeoGasto.persistence.entity.SavedPlace;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.LocationRepository;
import GeoGasto.persistence.repository.SavedPlaceRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public List<SavedPlaceResponse> listar(String email) {
        User user = obtenerUsuario(email);
        return savedPlaceRepository.findByUserIdOrderByUsageCountDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public SavedPlaceResponse crear(String email, String alias, Long locationId) {
        User user = obtenerUsuario(email);

        if (savedPlaceRepository.existsByUserIdAndAlias(user.getId(), alias)) {
            throw new IllegalArgumentException("Ya tienes un lugar guardado con ese alias");
        }

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));

        SavedPlace place = SavedPlace.builder()
                .alias(alias)
                .user(user)
                .location(location)
                .usageCount(0)
                .build();

        return toResponse(savedPlaceRepository.save(place));
    }

    @Transactional
    public void registrarUso(String email, Long id) {
        User user = obtenerUsuario(email);
        SavedPlace place = savedPlaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar guardado no encontrado"));

        if (!place.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Lugar guardado no encontrado");
        }

        place.setUsageCount(place.getUsageCount() + 1);
        savedPlaceRepository.save(place);
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        SavedPlace place = savedPlaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar guardado no encontrado"));

        if (!place.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Lugar guardado no encontrado");
        }
        savedPlaceRepository.delete(place);
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private SavedPlaceResponse toResponse(SavedPlace sp) {
        Location l = sp.getLocation();
        return SavedPlaceResponse.builder()
                .id(sp.getId())
                .alias(sp.getAlias())
                .locationId(l.getId())
                .direccion(l.getDireccion())
                .latitud(l.getLatitud())
                .longitud(l.getLongitud())
                .ciudad(l.getCiudad())
                .pais(l.getPais())
                .placeId(l.getPlaceId())
                .usageCount(sp.getUsageCount())
                .build();
    }
}