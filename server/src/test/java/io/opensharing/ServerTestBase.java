package io.opensharing;

import static org.hamcrest.Matchers.oneOf;
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

/**
 * Boots the whole server against an in-memory database and the file-backed catalog, registers the
 * principal the tests act as, and offers the provider-admin calls they need to set up a share.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:opensharing;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "opensharing.admin.bootstrap-token=" + ServerTestBase.BOOTSTRAP_TOKEN,
      "opensharing.catalog.type=local",
      "opensharing.catalog.local.file=classpath:test-catalog.yml",
      "opensharing.activation.external-base-url=https://sharing.example.com",
      "opensharing.protocol-prefix=/open-sharing"
    })
@AutoConfigureMockMvc
abstract class ServerTestBase {

  static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  static final String ADMIN_BASE = "/api/admin/v1";
  static final String PROTOCOL_BASE = "/open-sharing";

  /** The provider admin every test acts as, matching {@code sharableBy} in the test catalog. */
  static final String ALICE = "alice@example.com";

  static final String ALICE_TOKEN = "alice-secret";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;

  /** One database is shared by every test in a class, so Alice may already be registered. */
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

  /** Names have to be unique because one database is shared by every test in a class. */
  protected String unique(String prefix) {
    return prefix + "_" + Long.toHexString(System.nanoTime());
  }

  protected JsonNode adminPost(String path, String body) throws Exception {
    return admin(post(ADMIN_BASE + path), body);
  }

  protected JsonNode adminPatch(String path, String body) throws Exception {
    return admin(patch(ADMIN_BASE + path), body);
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

  protected String createShare(String name) throws Exception {
    adminPost("/shares", "{\"name\":\"" + name + "\",\"comment\":\"test share\"}");
    return name;
  }

  protected String createRecipient(String name) throws Exception {
    adminPost("/recipients", "{\"name\":\"" + name + "\"}");
    return name;
  }

  /** Creates a recipient and activates the token minted alongside it. */
  protected String createRecipientWithToken(String name) throws Exception {
    JsonNode created = adminPost("/recipients", "{\"name\":\"" + name + "\"}");
    return activate(created.get("token").get("activation_url").asText());
  }

  /** Rotates a recipient's token and activates the replacement. */
  protected String rotateToken(String recipient) throws Exception {
    return rotateToken(recipient, null);
  }

  protected String rotateToken(String recipient, String body) throws Exception {
    JsonNode issued = adminPost("/recipients/" + recipient + "/rotate-token", body);
    return activate(issued.get("activation_url").asText());
  }

  protected void grant(String share, String recipient) throws Exception {
    adminPatch(
        "/shares/" + share + "/permissions",
        "{\"changes\":[{\"recipient_name\":\"" + recipient + "\",\"add\":[\"SELECT\"]}]}");
  }

  protected void revoke(String share, String recipient) throws Exception {
    adminPatch(
        "/shares/" + share + "/permissions",
        "{\"changes\":[{\"recipient_name\":\"" + recipient + "\",\"remove\":[\"SELECT\"]}]}");
  }

  /** Adds a catalog table to a share under the alias recipients will see. */
  protected JsonNode addTable(String share, String sharedAs, String catalogName) throws Exception {
    JsonNode updated =
        adminPatch(
            "/shares/" + share,
            "{\"updates\":[{\"action\":\"ADD\",\"data_object\":{\"name\":\""
                + catalogName
                + "\",\"type\":\"TABLE\",\"shared_as\":\""
                + sharedAs
                + "\"}}]}");
    return objectSharedAs(updated, sharedAs);
  }

  protected static JsonNode objectSharedAs(JsonNode share, String sharedAs) {
    for (JsonNode object : share.get("objects")) {
      if (object.get("shared_as").asText().equalsIgnoreCase(sharedAs)) {
        return object;
      }
    }
    throw new AssertionError("'" + sharedAs + "' is not shared in " + share);
  }

  /** Opens an activation URL and returns the bearer token from the profile file it hands back. */
  protected String activate(String activationUrl) throws Exception {
    JsonNode profile =
        readJson(
            mvc.perform(get("/activation/" + nonceOf(activationUrl)))
                .andExpect(status().isOk())
                .andReturn());
    return profile.get("bearerToken").asText();
  }

  protected static String nonceOf(String activationUrl) {
    return activationUrl.substring(activationUrl.lastIndexOf('/') + 1);
  }

  protected JsonNode protocolGet(String token, String path) throws Exception {
    return readJson(
        mvc.perform(get(PROTOCOL_BASE + path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn());
  }

  protected JsonNode protocolPost(String token, String path) throws Exception {
    return readJson(
        mvc.perform(post(PROTOCOL_BASE + path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn());
  }

  protected JsonNode readJson(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
