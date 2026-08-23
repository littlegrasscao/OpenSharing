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
 * <p>One secret, kept two ways, because the server does two different things with it. Recognizing the
 * principal when they present it needs no more than a SHA-256 hash. Presenting it to the catalog while
 * serving a recipient, long after their own request ended, needs it back in the clear, so a second
 * copy is stored encrypted. Neither is ever returned.
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
   * The same secret as {@link #tokenHash}, kept in a form the server can read back, because serving a
   * recipient means presenting it to the catalog long after the request that carried it. Encrypted
   * with the deployment's key. Only a row older than this arrangement has none, and nothing such a
   * principal shares can be served.
   *
   * <p>Sized for the longest token {@code PrincipalStore} accepts once sealed, which is longer than
   * the token itself: a nonce, a tag and base64 on top of it. A hash needed no such room, so the
   * column that holds one is the fixed 64 above; this one has to hold whatever a catalog issues, and
   * a JWT runs to a few thousand characters.
   */
  @Column(name = "catalog_credential", length = 4096)
  private String catalogCredential;

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

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getCatalogCredential() {
    return catalogCredential;
  }

  public void setCatalogCredential(String catalogCredential) {
    this.catalogCredential = catalogCredential;
  }
}
