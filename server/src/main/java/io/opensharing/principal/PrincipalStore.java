package io.opensharing.principal;

import io.opensharing.ObjectNames;
import io.opensharing.auth.SecretCipher;
import io.opensharing.auth.Secrets;
import io.opensharing.catalog.CatalogCaller;
import io.opensharing.http.ApiException;
import io.opensharing.http.ErrorCodes;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Storage for principals. Name lookups are case-insensitive, as they are for every other object. */
@Service
@Transactional
public class PrincipalStore {

  private static final Logger log = LoggerFactory.getLogger(PrincipalStore.class);

  /**
   * Longest token accepted, chosen so its sealed form fits {@code PrincipalEntity}'s column with room
   * to spare: 2048 characters seal to under 2800, against 4096 there.
   */
  private static final int MAX_TOKEN_LENGTH = 2048;

  private final PrincipalRepository principals;
  private final EntityManager entityManager;
  private final SecretCipher cipher;

  public PrincipalStore(
      PrincipalRepository principals, EntityManager entityManager, SecretCipher cipher) {
    this.principals = principals;
    this.entityManager = entityManager;
    this.cipher = cipher;
  }

  /**
   * @param id the id to register under, or null to generate one
   * @param bearerToken the one secret a principal has, kept in the two forms {@link #storeToken}
   *     explains
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
    storeToken(principal, bearerToken);
    // An insert rather than a save, because saving a caller-supplied id that is already taken would
    // merge into that principal and quietly replace its name and token.
    entityManager.persist(principal);
    return principal;
  }

  /**
   * A principal's token is stored twice because the server does two different things with it. To let
   * them in, it only has to recognize a secret they have just presented, which a hash does without
   * being able to give it back. To ask the catalog as them while serving a recipient, long after their
   * own request ended, it has to present the secret itself, which only encryption allows.
   *
   * <p>Two stored forms, then, but one secret: it is the provider's catalog credential, and this
   * server accepts it as a login because whoever holds it can already act as them against the catalog
   * that decides everything here anyway. The cost is that the encrypted form is recoverable by whoever
   * holds both the database and the key, which the hash alone would not have allowed — see {@link
   * SecretCipher} for why the key is meant to live somewhere a database dump does not reach.
   *
   * <p>Sealed to the principal's id, which they have from the moment they are constructed and cannot
   * change afterwards, so a sealed credential moved to another row will not be read there.
   */
  private void storeToken(PrincipalEntity principal, String bearerToken) {
    principal.setTokenHash(Secrets.sha256(bearerToken));
    principal.setCatalogCredential(cipher.encrypt(bearerToken, principal.getId()));
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

  /**
   * Ensures a configured principal exists with the given token. Creates on first run and replaces the
   * stored token when the configuration changes.
   */
  public PrincipalEntity provision(PrincipalType type, String name, String bearerToken) {
    ObjectNames.validatePrincipalName(name);
    requireToken(bearerToken);
    return principals
        .findByNameLower(ObjectNames.normalize(name))
        .map(
            principal -> {
              if (principals
                  .findByTokenHash(Secrets.sha256(bearerToken))
                  .filter(existing -> existing.getId().equals(principal.getId()))
                  .isPresent()) {
                return principal;
              }
              storeToken(principal, bearerToken);
              return principals.save(principal);
            })
        .orElseGet(() -> create(null, type, name, bearerToken));
  }

  /**
   * How the catalog is reached on behalf of a principal who is not the one asking — the owner of a
   * share whose recipient is reading a table. Their stored credential is decrypted for this one call
   * and held nowhere else.
   *
   * <p>Every principal registered through this store has one, since it is the same secret they log in
   * with. A row that predates that, or one written by a build that kept the two apart, can still have
   * none, and then the read stops: asking the catalog as the server's own identity instead would
   * answer a question nobody asked, succeeding on the server's access rather than the owner's, so a
   * provider who lost the asset would go on serving it and every recipient would read by a privilege
   * no provider was ever granted.
   *
   * <p>Only a recipient's read comes through here, so the answer is written for one: which principal
   * is short a credential, and what to do about it, are for whoever runs the server, and go to the log
   * rather than into a response somebody outside the organization reads.
   */
  public CatalogCaller catalogCallerFor(PrincipalEntity principal) {
    String stored = principal.getCatalogCredential();
    if (stored == null) {
      log.error(
          "No catalog credential is stored for '{}', so nothing they share can be served; set their "
              + "bearer token in opensharing.admin.principals",
          principal.getName());
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCodes.INTERNAL_ERROR,
          "this table cannot be served at the moment, because the provider it is shared by has no "
              + "credential stored to read it with");
    }
    return CatalogCaller.of(principal.getName(), cipher.decrypt(stored, principal.getId()));
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

  /**
   * The upper bound is the column's, worked back through sealing: a token is stored encrypted as well
   * as hashed, and that form is longer than the token by a nonce, a tag and base64. Said here, where
   * the number can be explained, rather than left to the insert — a value too long for a column
   * arrives as a constraint violation, which is answered as a conflict, which would tell an
   * administrator their principal already exists when the truth is that their token does not fit.
   */
  private static void requireToken(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      throw ApiException.invalidParameter("bearer_token must not be blank");
    }
    if (bearerToken.length() > MAX_TOKEN_LENGTH) {
      throw ApiException.invalidParameter(
          "bearer_token is "
              + bearerToken.length()
              + " characters, and this server stores one of at most "
              + MAX_TOKEN_LENGTH);
    }
  }
}
