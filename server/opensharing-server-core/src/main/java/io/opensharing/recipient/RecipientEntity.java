package io.opensharing.recipient;

import io.opensharing.BaseEntity;
import io.opensharing.ObjectNames;
import io.opensharing.principal.PrincipalEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An organization or principal that consumes shares. */
@Entity
@Table(
    name = "recipients",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_recipients_name_lower", columnNames = "name_lower"))
public class RecipientEntity extends BaseEntity {

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "name_lower", nullable = false, length = 255)
  private String nameLower;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_type", nullable = false, length = 16)
  private AuthType authType = AuthType.TOKEN;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private PrincipalEntity owner;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private PrincipalEntity createdBy;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "updated_by", nullable = false)
  private PrincipalEntity updatedBy;

  /** CIDR blocks the recipient may connect from. Empty means anywhere. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "recipient_ip_access_list",
      joinColumns = @JoinColumn(name = "recipient_id"))
  @Column(name = "cidr", length = 64)
  private List<String> ipAccessList = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "recipient_properties",
      joinColumns = @JoinColumn(name = "recipient_id"),
      uniqueConstraints =
          @UniqueConstraint(
              name = "uk_recipient_properties",
              columnNames = {"recipient_id", "property_key"}))
  @MapKeyColumn(name = "property_key", length = 255)
  @Column(name = "property_value", length = 1000)
  private Map<String, String> properties = new LinkedHashMap<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
    this.nameLower = ObjectNames.normalize(name);
  }

  public AuthType getAuthType() {
    return authType;
  }

  public void setAuthType(AuthType authType) {
    this.authType = authType;
  }

  public PrincipalEntity getOwner() {
    return owner;
  }

  public void setOwner(PrincipalEntity owner) {
    this.owner = owner;
  }

  public PrincipalEntity getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(PrincipalEntity createdBy) {
    this.createdBy = createdBy;
  }

  public PrincipalEntity getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(PrincipalEntity updatedBy) {
    this.updatedBy = updatedBy;
  }

  /** Read-only, for the same reason as {@link #getProperties()}. */
  public List<String> getIpAccessList() {
    return Collections.unmodifiableList(ipAccessList);
  }

  public void setIpAccessList(List<String> ipAccessList) {
    this.ipAccessList = ipAccessList == null ? new ArrayList<>() : new ArrayList<>(ipAccessList);
  }

  /**
   * Read-only, so a caller reading a recipient's properties cannot quietly rewrite the row behind
   * it: what Hibernate hands back is the persistent collection itself, and a change to it is a
   * change to the entity whether or not anyone meant one.
   */
  public Map<String, String> getProperties() {
    return Collections.unmodifiableMap(properties);
  }

  public void setProperties(Map<String, String> properties) {
    this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
  }
}
