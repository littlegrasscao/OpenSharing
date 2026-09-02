package io.opensharing.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.opensharing.catalog.CatalogConnector;
import io.opensharing.catalog.local.LocalCatalogConnector;
import io.opensharing.catalog.local.LocalCatalogLoader;
import io.opensharing.principal.Caller;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:opensharing-embedded;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "opensharing.hosting.mode=embedded",
      "opensharing.security.credential-encryption-key=c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=",
      "opensharing.protocol-prefix=/opensharing"
    })
@AutoConfigureMockMvc
class EmbeddedOpenSharingTest {

  @Autowired private MockMvc mvc;
  @Autowired private SharingRuntime runtime;

  @Test
  void bootsEmbeddedWithHostSuppliedCatalogAndIdentity() throws Exception {
    assertEquals(HostingMode.EMBEDDED, runtime.hostingMode());
    assertEquals("local", runtime.catalogConnector().name());

    mvc.perform(get("/api/admin/v1/shares").header("Authorization", "Bearer host-token"))
        .andExpect(status().isOk());
  }

  @TestConfiguration
  static class HostBeans {

    @Bean
    CatalogConnector catalogConnector() throws Exception {
      try (InputStream in = getClass().getResourceAsStream("/test-catalog.yml")) {
        return new LocalCatalogConnector(LocalCatalogLoader.load(in, "test-catalog.yml"));
      }
    }

    @Bean
    ProviderIdentityResolver providerIdentityResolver() {
      return request -> Optional.of(new Caller("host-principal-id", "uc-admin@example.com", "host-token"));
    }
  }
}
