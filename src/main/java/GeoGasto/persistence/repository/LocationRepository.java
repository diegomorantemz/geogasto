package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByIdAndUserId(Long id, Long userId);
}