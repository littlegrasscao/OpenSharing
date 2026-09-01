package io.opensharing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "opensharing.admin.bootstrap-token=test-bootstrap",
      "opensharing.pagination.default-max-results=100",
      "opensharing.pagination.max-max-results=200"
    })
class OpenSharingPropertiesTest {

  @Autowired private OpenSharingProperties properties;

  @Test
  void bindsAdminAndPaginationSettings() {
    assertEquals("/api/admin/v1", properties.getAdmin().getBasePath());
    assertEquals("test-bootstrap", properties.getAdmin().getBootstrapToken());
    assertEquals(100, properties.getPagination().getDefaultMaxResults());
    assertEquals(200, properties.getPagination().getMaxMaxResults());
  }
}
