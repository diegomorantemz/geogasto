package GeoGasto.persistence.repository;

import GeoGasto.persistence.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    List<SavedPlace> findByUserIdOrderByUsageCountDesc(Long userId);

    boolean existsByUserIdAndAlias(Long userId, String alias);

    boolean existsByUserIdAndAliasIgnoreCase(Long userId, String alias);

    boolean existsByUserIdAndAliasIgnoreCaseAndIdNot(Long userId, String alias, Long id);

    boolean existsByLocationId(Long locationId);
}
