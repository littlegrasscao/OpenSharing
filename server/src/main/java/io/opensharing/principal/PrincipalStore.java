package io.opensharing.principal;

import io.opensharing.ObjectNames;
import io.opensharing.auth.Secrets;
import io.opensharing.http.ApiException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Storage for principals. Name lookups are case-insensitive, as they are for every other object. */
@Service
@Transactional
public class PrincipalStore {

  private final PrincipalRepository principals;
  private final List<PrincipalUsage> usages;
  private final EntityManager entityManager;

  public PrincipalStore(
      PrincipalRepository principals, List<PrincipalUsage> usages, EntityManager entityManager) {
    this.principals = principals;
    this.usages = usages;
    this.entityManager = entityManager;
  }

  /**
   * @param id the id to register under, or null to generate one
   */
  public PrincipalEntity create(String id, PrincipalType type, String name, String bearerToken) {
    ObjectNames.validatePrincipalName(name);
    requireToken(bearerToken);
    if (principals.existsByNameLower(ObjectNames.normalize(name))) {
      throw ApiException.alreadyExists("principal '" + name + "' already exists");
    }
    PrincipalEntity principal = new PrincipalEntity();
    if (id != null && !id.isBlank()) {
      principal.setId(claimId(id));
    }
    principal.setType(type);
    principal.setName(name);
    principal.setTokenHash(Secrets.sha256(bearerToken));
    // An insert rather than a save, because saving a caller-supplied id that is already taken would
    // merge into that principal and quietly replace its name and token.
    entityManager.persist(principal);
    return principal;
  }

  /** Registering under a chosen id must not take one that is already spoken for. */
  private String claimId(String id) {
    String canonical;
    try {
      canonical = UUID.fromString(id).toString();
    } catch (IllegalArgumentException e) {
      throw ApiException.invalidParameter("id '" + id + "' is not a UUID");
    }
    if (principals.existsById(canonical)) {
      throw ApiException.alreadyExists("a principal with id '" + canonical + "' already exists");
    }
    return canonical;
  }

  /** Only non-null fields are applied. Replacing the token invalidates the previous one at once. */
  public PrincipalEntity update(String name, String newName, String bearerToken) {
    PrincipalEntity principal = require(name);
    if (newName != null && !ObjectNames.normalize(newName).equals(principal.getNameLower())) {
      ObjectNames.validatePrincipalName(newName);
      if (principals.existsByNameLower(ObjectNames.normalize(newName))) {
        throw ApiException.alreadyExists("principal '" + newName + "' already exists");
      }
      principal.setName(newName);
    }
    if (bearerToken != null) {
      requireToken(bearerToken);
      principal.setTokenHash(Secrets.sha256(bearerToken));
    }
    return principals.save(principal);
  }

  @Transactional(readOnly = true)
  public PrincipalEntity require(String name) {
    return principals
        .findByNameLower(ObjectNames.normalize(name))
        .orElseThrow(() -> ApiException.notFound("principal '" + name + "' does not exist"));
  }

  @Transactional(readOnly = true)
  public Optional<PrincipalEntity> findByToken(String bearerToken) {
    return principals.findByTokenHash(Secrets.sha256(bearerToken));
  }

  @Transactional(readOnly = true)
  public PrincipalEntity requireById(String id) {
    return principals
        .findById(id)
        .orElseThrow(() -> ApiException.unauthenticated("the principal no longer exists"));
  }

  /**
   * The principal behind an authenticated request, which is who its writes are recorded as. Every
   * admin endpoint that records authorship asks this, so it is answered once rather than in each.
   */
  @Transactional(readOnly = true)
  public PrincipalEntity require(Caller caller) {
    return requireById(caller.principalId());
  }

  @Transactional(readOnly = true)
  public Page<PrincipalEntity> list(Pageable pageable) {
    return principals.findAllByOrderByNameLowerAsc(pageable);
  }

  /**
   * Deleting a principal is refused while anything still points at it, since shares and recipients
   * record who owns and who authored them, and an audit trail that names nobody is worse than one
   * that names someone who has left.
   */
  public void delete(String name) {
    PrincipalEntity principal = require(name);
    List<String> references =
        usages.stream()
            .flatMap(usage -> usage.describeReferencesTo(principal).stream())
            .sorted()
            .toList();
    if (!references.isEmpty()) {
      throw ApiException.conflict(
          "principal '"
              + principal.getName()
              + "' still has "
              + String.join(", ", references)
              + "; delete those first");
    }
    principals.delete(principal);
  }

  private static void requireToken(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      throw ApiException.invalidParameter("bearer_token must not be blank");
    }
  }
}
