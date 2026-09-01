package io.opensharing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server configuration under the {@code opensharing} prefix. */
@ConfigurationProperties(prefix = "opensharing")
public class OpenSharingProperties {

  private final Admin admin = new Admin();
  private final Pagination pagination = new Pagination();

  public Admin getAdmin() {
    return admin;
  }

  public Pagination getPagination() {
    return pagination;
  }

  /** Provider-admin API settings. */
  public static class Admin {

    /** URL prefix for the provider-admin API. */
    private String basePath = "/api/admin/v1";

    /**
     * Token that registers the first principals, before anyone has one of their own. It is accepted
     * only on {@code /principals}: everything else records an owner, so it needs a real principal. A
     * random one is generated at startup when blank.
     */
    private String bootstrapToken;

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = basePath;
    }

    public String getBootstrapToken() {
      return bootstrapToken;
    }

    public void setBootstrapToken(String bootstrapToken) {
      this.bootstrapToken = bootstrapToken;
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
}
