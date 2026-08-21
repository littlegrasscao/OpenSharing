package io.opensharing.recipient;

import io.opensharing.principal.PrincipalEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<RecipientEntity, String> {

  Optional<RecipientEntity> findByNameLower(String nameLower);

  boolean existsByNameLower(String nameLower);

  Page<RecipientEntity> findAllByOrderByNameLowerAsc(Pageable pageable);

  long countByOwnerOrCreatedByOrUpdatedBy(
      PrincipalEntity owner, PrincipalEntity createdBy, PrincipalEntity updatedBy);
}
