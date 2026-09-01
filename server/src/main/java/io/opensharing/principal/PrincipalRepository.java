package io.opensharing.principal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepository extends JpaRepository<PrincipalEntity, String> {

  Optional<PrincipalEntity> findByNameLower(String nameLower);

  Optional<PrincipalEntity> findByTokenHash(String tokenHash);

  boolean existsByNameLower(String nameLower);

  Page<PrincipalEntity> findAllByOrderByNameLowerAsc(Pageable pageable);
}
