package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    List<SavedPlace> findByUserIdOrderByUsageCountDesc(Long userId);

    Optional<SavedPlace> findByUserIdAndAlias(Long userId, String alias);

    boolean existsByUserIdAndAlias(Long userId, String alias);
}