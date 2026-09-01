package io.opensharing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opensharing.principal.PrincipalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "opensharing.security.credential-encryption-key=c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=",
      "opensharing.principals[0].name=alice@example.com",
      "opensharing.principals[0].bearer-token=alice-secret",
      "opensharing.principals[0].type=USER",
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
    assertEquals("alice-secret", properties.getPrincipals().get(0).getBearerToken());
    assertEquals(PrincipalType.USER, properties.getPrincipals().get(0).getType());
    assertEquals(
        "c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=",
        properties.getSecurity().getCredentialEncryptionKey());
    assertEquals(100, properties.getPagination().getDefaultMaxResults());
    assertEquals(200, properties.getPagination().getMaxMaxResults());
  }
}
