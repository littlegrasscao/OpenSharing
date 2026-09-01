package io.opensharing;

import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:opensharing;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "opensharing.admin.bootstrap-token=" + PrincipalTestBase.BOOTSTRAP_TOKEN
    })
@AutoConfigureMockMvc
abstract class PrincipalTestBase {

  static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  static final String ADMIN_BASE = "/api/admin/v1";
  static final String ALICE = "alice@example.com";
  static final String ALICE_TOKEN = "alice-secret";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;

  @BeforeEach
  void registerAlice() throws Exception {
    mvc.perform(
            post(ADMIN_BASE + "/principals")
                .header("Authorization", "Bearer " + BOOTSTRAP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"USER\",\"name\":\""
                        + ALICE
                        + "\",\"bearer_token\":\""
                        + ALICE_TOKEN
                        + "\"}"))
        .andExpect(status().is(oneOf(201, 409)));
  }

  protected String unique(String prefix) {
    return prefix + "_" + Long.toHexString(System.nanoTime());
  }

  protected JsonNode adminGet(String path) throws Exception {
    return admin(get(ADMIN_BASE + path), null);
  }

  private JsonNode admin(MockHttpServletRequestBuilder request, String body) throws Exception {
    request.header("Authorization", "Bearer " + ALICE_TOKEN);
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return readJson(mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn());
  }

  protected MockHttpServletRequestBuilder bootstrap(MockHttpServletRequestBuilder request) {
    return request
        .header("Authorization", "Bearer " + BOOTSTRAP_TOKEN)
        .contentType(MediaType.APPLICATION_JSON);
  }

  protected MockHttpServletRequestBuilder adminJson(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + ALICE_TOKEN).contentType(MediaType.APPLICATION_JSON);
  }

  protected JsonNode readJson(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
