package io.opensharing.config;

import io.opensharing.catalog.CatalogConnector;
import io.opensharing.catalog.CatalogException;
import io.opensharing.catalog.local.LocalCatalogConnector;
import io.opensharing.catalog.local.LocalCatalogFile;
import io.opensharing.catalog.local.LocalCatalogLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Selects the {@link CatalogConnector} the server runs with. The prototype ships only the
 * file-backed connector; a deployment that talks to a real catalog contributes its own {@code
 * CatalogConnector} bean, which takes precedence over this one.
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
    if (!type.equals(LocalCatalogConnector.NAME)) {
      throw new IllegalStateException(
          "unknown opensharing.catalog.type '"
              + catalog.getType()
              + "'; this build only ships the '"
              + LocalCatalogConnector.NAME
              + "' connector");
    }
    CatalogConnector connector = localConnector(catalog.getLocal(), resourceLoader);
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
}
