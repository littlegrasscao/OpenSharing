package io.opensharing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opensharing.auth.CatalogAuthType;
import io.opensharing.principal.PrincipalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:opensharing-properties-test;DB_CLOSE_DELAY=-1",
      "opensharing.security.credential-encryption-key=c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=",
      "opensharing.principals[0].name=alice@example.com",
      "opensharing.principals[0].id=11111111-1111-1111-1111-111111111111",
      "opensharing.principals[0].type=USER",
      "opensharing.principals[0].auth-type=TOKEN",
      "opensharing.principals[0].bearer-token=alice-secret",
      "opensharing.pagination.default-max-results=100",
      "opensharing.pagination.max-max-results=200"
    })
class OpenSharingPropertiesTest {

  @Autowired private OpenSharingProperties properties;

  @Test
  void bindsApiPrincipalsSecurityAndPaginationSettings() {
    assertEquals("/api/v1", properties.getApi().getBasePath());
    assertEquals(1, properties.getPrincipals().size());
    assertEquals("alice@example.com", properties.getPrincipals().get(0).getName());
    assertEquals(
        "11111111-1111-1111-1111-111111111111", properties.getPrincipals().get(0).getId());
    assertEquals(PrincipalType.USER, properties.getPrincipals().get(0).getType());
    assertEquals(CatalogAuthType.TOKEN, properties.getPrincipals().get(0).getAuthType());
    assertEquals("alice-secret", properties.getPrincipals().get(0).getBearerToken());
    assertEquals(
        "c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=",
        properties.getSecurity().getCredentialEncryptionKey());
    assertEquals(100, properties.getPagination().getDefaultMaxResults());
    assertEquals(200, properties.getPagination().getMaxMaxResults());
  }
}
