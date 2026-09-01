package io.opensharing.principal;

import io.opensharing.BaseEntity;
import io.opensharing.ObjectNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A user or group that administers sharing. Every provider-admin call authenticates as one, and the
 * server records it as the owner and author of what the call creates.
 *
 * <p>Only a SHA-256 hash of the principal's bearer token is stored, so the plaintext exists only in
 * the request that presents it. A consequence worth knowing: the server can query the catalog as the
 * caller while that request is in flight, but never afterwards.
 */
@Entity
@Table(
    name = "principals",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_principals_name_lower", columnNames = "name_lower"),
      @UniqueConstraint(name = "uk_principals_token", columnNames = "token_hash")
    })
public class PrincipalEntity extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private PrincipalType type;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "name_lower", nullable = false, length = 255)
  private String nameLower;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  /**
   * A principal may be registered under an id the caller chose, so it can carry the id an external
   * directory already uses. Shares and recipients are the other way round: their ids are the server's.
   */
  @Override
  public void setId(String id) {
    super.setId(id);
  }

  public PrincipalType getType() {
    return type;
  }

  public void setType(PrincipalType type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
    this.nameLower = ObjectNames.normalize(name);
  }

  public String getNameLower() {
    return nameLower;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }
}
