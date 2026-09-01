package io.opensharing.config;

import io.opensharing.principal.PrincipalType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server configuration under the {@code opensharing} prefix. */
@ConfigurationProperties(prefix = "opensharing")
public class OpenSharingProperties {

  private final Api api = new Api();
  private final Security security = new Security();
  private final Pagination pagination = new Pagination();

  /**
   * Provider principals the server recognizes. Each entry is registered in the database at startup,
   * and its bearer token is both the provider API login and the credential presented to the catalog.
   */
  private List<Principal> principals = List.of();

  public Api getApi() {
    return api;
  }

  public Security getSecurity() {
    return security;
  }

  public Pagination getPagination() {
    return pagination;
  }

  public List<Principal> getPrincipals() {
    return principals;
  }

  public void setPrincipals(List<Principal> principals) {
    this.principals = principals == null ? List.of() : List.copyOf(principals);
  }

  /** Provider API URL prefix. */
  public static class Api {

    private String basePath = "/api/v1";

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = prefix(basePath);
    }
  }

  /** One username and credential the server provisions at startup. */
  public static class Principal {

    private String name;
    private String bearerToken;
    private PrincipalType type;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getBearerToken() {
      return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
      this.bearerToken = bearerToken;
    }

    public PrincipalType getType() {
      return type;
    }

    public void setType(PrincipalType type) {
      this.type = type;
    }
  }

  public static class Security {

    /**
     * Base64 AES key (16, 24 or 32 bytes) that encrypts and decrypts principal bearer tokens in
     * storage; the server uses the decrypted token to call the catalog on recipient API requests.
     */
    private String credentialEncryptionKey;

    public String getCredentialEncryptionKey() {
      return credentialEncryptionKey;
    }

    public void setCredentialEncryptionKey(String credentialEncryptionKey) {
      this.credentialEncryptionKey = credentialEncryptionKey;
    }
  }

  /** Bounds on {@code maxResults} for list endpoints. */
  public static class Pagination {

    private int defaultMaxResults = 500;
    private int maxMaxResults = 1000;

    public int getDefaultMaxResults() {
      return defaultMaxResults;
    }

    public void setDefaultMaxResults(int defaultMaxResults) {
      this.defaultMaxResults = defaultMaxResults;
    }

    public int getMaxMaxResults() {
      return maxMaxResults;
    }

    public void setMaxMaxResults(int maxMaxResults) {
      this.maxMaxResults = maxMaxResults;
    }
  }

  private static String prefix(String value) {
    return value != null && value.length() > 1 && value.endsWith("/")
        ? value.substring(0, value.length() - 1)
        : value;
  }
}
