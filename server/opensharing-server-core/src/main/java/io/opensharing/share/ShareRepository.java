package io.opensharing.share;

import io.opensharing.principal.PrincipalEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<ShareEntity, String> {

  Optional<ShareEntity> findByNameLower(String nameLower);

  boolean existsByNameLower(String nameLower);

  Page<ShareEntity> findAllByOrderByNameLowerAsc(Pageable pageable);

  long countByOwnerOrCreatedByOrUpdatedBy(
      PrincipalEntity owner, PrincipalEntity createdBy, PrincipalEntity updatedBy);
}
