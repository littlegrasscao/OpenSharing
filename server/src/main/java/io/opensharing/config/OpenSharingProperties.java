package io.opensharing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server configuration under the {@code opensharing} prefix. */
@ConfigurationProperties(prefix = "opensharing")
public class OpenSharingProperties {

  /** URL prefix the recipient-facing protocol endpoints are mounted under. */
  private String protocolPrefix = "/open-sharing";

  private final Admin admin = new Admin();
  private final Activation activation = new Activation();
  private final RecipientTokens recipientTokens = new RecipientTokens();
  private final AssetCredentials assetCredentials = new AssetCredentials();
  private final Pagination pagination = new Pagination();
  private final Delta delta = new Delta();
  private final Catalog catalog = new Catalog();

  public String getProtocolPrefix() {
    return protocolPrefix;
  }

  public void setProtocolPrefix(String protocolPrefix) {
    this.protocolPrefix = protocolPrefix;
  }

  public Admin getAdmin() {
    return admin;
  }

  public Activation getActivation() {
    return activation;
  }

  public RecipientTokens getRecipientTokens() {
    return recipientTokens;
  }

  public AssetCredentials getAssetCredentials() {
    return assetCredentials;
  }

  public Pagination getPagination() {
    return pagination;
  }

  public Delta getDelta() {
    return delta;
  }

  public Catalog getCatalog() {
    return catalog;
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

  /** One-time activation URLs that hand a profile file to a recipient. */
  public static class Activation {

    private String basePath = "/activation";

    /** How long an unused activation link stays valid. */
    private Duration ttl = Duration.ofHours(72);

    /** Base URL recipients can reach, used to build activation URLs and the profile endpoint. */
    private String externalBaseUrl = "http://localhost:8080";

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = basePath;
    }

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl;
    }

    public String getExternalBaseUrl() {
      return externalBaseUrl;
    }

    public void setExternalBaseUrl(String externalBaseUrl) {
      this.externalBaseUrl = externalBaseUrl;
    }
  }

  /** Bearer tokens issued to recipients. */
  public static class RecipientTokens {

    /** Lifetime applied when a token is minted without an explicit expiration. Null never expires. */
    private Duration defaultTtl = Duration.ofDays(90);

    /**
     * How long a superseded token keeps working when a rotation request does not say. Gives the
     * recipient a window to install the new profile file; zero cuts it off at once.
     */
    private Duration rotationGrace = Duration.ofHours(24);

    public Duration getDefaultTtl() {
      return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
      this.defaultTtl = defaultTtl;
    }

    public Duration getRotationGrace() {
      return rotationGrace;
    }

    public void setRotationGrace(Duration rotationGrace) {
      this.rotationGrace = rotationGrace;
    }
  }

  /** Lifetime requested from the catalog when vending storage credentials. */
  public static class AssetCredentials {

    private Duration ttl = Duration.ofHours(1);

    public Duration getTtl() {
      return ttl;
    }

    public void setTtl(Duration ttl) {
      this.ttl = ttl;
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

  /** Serving Delta tables by url: reading their log and handing out signed file urls. */
  public static class Delta {

    /**
     * Whether to read Delta logs and serve {@code query}. Turning this off leaves dir access mode,
     * where a recipient reads the log itself with vended credentials, and stops {@code url} being
     * advertised on any table.
     */
    private boolean urlAccessEnabled = true;

    /** How long a signed file url stays valid. Never outlives the credentials it was signed with. */
    private Duration urlTtl = Duration.ofHours(1);

    /** Region used to sign S3 urls when the catalog's credentials do not name one. */
    private String s3Region = "us-east-1";

    public boolean isUrlAccessEnabled() {
      return urlAccessEnabled;
    }

    public void setUrlAccessEnabled(boolean urlAccessEnabled) {
      this.urlAccessEnabled = urlAccessEnabled;
    }

    public Duration getUrlTtl() {
      return urlTtl;
    }

    public void setUrlTtl(Duration urlTtl) {
      this.urlTtl = urlTtl;
    }

    public String getS3Region() {
      return s3Region;
    }

    public void setS3Region(String s3Region) {
      this.s3Region = s3Region;
    }
  }

  /** Which {@code CatalogConnector} to run with. */
  public static class Catalog {

    /** Connector id. The prototype only ships {@code local}. */
    private String type = "local";

    private final Local local = new Local();

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Local getLocal() {
      return local;
    }

    /** File-backed connector for local development. */
    public static class Local {

      /** Spring resource location of the catalog file, e.g. {@code file:./catalog.yml}. */
      private String file = "classpath:local-catalog.yml";

      public String getFile() {
        return file;
      }

      public void setFile(String file) {
        this.file = file;
      }
    }
  }
}
