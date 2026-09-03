package io.opensharing.recipient;

import io.opensharing.principal.PrincipalEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientTokenRepository extends JpaRepository<RecipientTokenEntity, String> {

  Optional<RecipientTokenEntity> findByTokenHash(String tokenHash);

  Optional<RecipientTokenEntity> findByActivationNonceHash(String activationNonceHash);

  List<RecipientTokenEntity> findByRecipientOrderByCreatedAtDesc(RecipientEntity recipient);

  List<RecipientTokenEntity> findByRecipientAndRevokedAtIsNull(RecipientEntity recipient);

  long countByCreatedBy(PrincipalEntity createdBy);

  void deleteByRecipient(RecipientEntity recipient);
}
