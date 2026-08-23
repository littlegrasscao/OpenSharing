package io.opensharing.config;

import io.opensharing.catalog.CatalogConnector;
import io.opensharing.catalog.CatalogException;
import io.opensharing.catalog.local.LocalCatalogConnector;
import io.opensharing.catalog.local.LocalCatalogFile;
import io.opensharing.catalog.local.LocalCatalogLoader;
import io.opensharing.catalog.unity.UnityCatalogConnector;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Selects the {@link CatalogConnector} the server runs with. Two ship: the file-backed one for local
 * development, and Unity Catalog. A deployment that talks to something else contributes its own
 * {@code CatalogConnector} bean, which takes precedence over this one.
 */
@Configuration
public class CatalogConfiguration {

  private static final Logger log = LoggerFactory.getLogger(CatalogConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(CatalogConnector.class)
  public CatalogConnector catalogConnector(
      OpenSharingProperties properties, ResourceLoader resourceLoader) {
    OpenSharingProperties.Catalog catalog = properties.getCatalog();
    String type = catalog.getType() == null ? "" : catalog.getType().trim().toLowerCase(Locale.ROOT);
    CatalogConnector connector =
        switch (type) {
          case LocalCatalogConnector.NAME -> localConnector(catalog.getLocal(), resourceLoader);
          case UnityCatalogConnector.NAME -> unityConnector(catalog.getUnity());
          default ->
              throw new IllegalStateException(
                  "unknown opensharing.catalog.type '"
                      + catalog.getType()
                      + "'; this build ships '"
                      + LocalCatalogConnector.NAME
                      + "' and '"
                      + UnityCatalogConnector.NAME
                      + "'");
        };
    log.info("Using '{}' catalog connector", connector.name());
    return connector;
  }

  private CatalogConnector localConnector(
      OpenSharingProperties.Catalog.Local config, ResourceLoader resourceLoader) {
    Resource resource = resourceLoader.getResource(config.getFile());
    if (!resource.exists()) {
      throw new IllegalStateException(
          "local catalog file '" + config.getFile() + "' does not exist");
    }
    try (InputStream in = resource.getInputStream()) {
      LocalCatalogFile file = LocalCatalogLoader.load(in, config.getFile());
      return new LocalCatalogConnector(file);
    } catch (IOException e) {
      throw new CatalogException("failed to read local catalog file " + config.getFile(), e);
    }
  }

  /**
   * The url is checked here rather than on the first request, so that a server pointed at nothing —
   * or at a host with no scheme, which is the easy mistake — says so at startup instead of when a
   * recipient first reads a table.
   */
  private CatalogConnector unityConnector(OpenSharingProperties.Catalog.Unity config) {
    String uri = config.getUri() == null ? "" : config.getUri().trim();
    if (uri.isBlank()) {
      throw new IllegalStateException(
          "opensharing.catalog.unity.uri is required to run with the '"
              + UnityCatalogConnector.NAME
              + "' connector, e.g. http://localhost:8081/api/2.1/unity-catalog");
    }
    URI parsed = URI.create(uri);
    if (!parsed.isAbsolute() || parsed.getHost() == null) {
      throw new IllegalStateException(
          "opensharing.catalog.unity.uri '" + uri + "' is not an absolute http or https url");
    }
    return new UnityCatalogConnector(
        parsed, config.getConnectTimeout(), config.getRequestTimeout());
  }
}
