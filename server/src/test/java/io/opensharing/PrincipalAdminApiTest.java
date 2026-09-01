package io.opensharing;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PrincipalAdminApiTest extends PrincipalTestBase {

  @Test
  void requiresAPrincipalsToken() throws Exception {
    mvc.perform(get(ADMIN_BASE + "/principals"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

    mvc.perform(get(ADMIN_BASE + "/principals").header("Authorization", "Bearer wrong-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void letsTheBootstrapTokenRegisterPrincipalsAndNothingElse() throws Exception {
    String name = unique("bob") + "@example.com";
    JsonNode created =
        readJson(
            mvc.perform(
                    bootstrap(post(ADMIN_BASE + "/principals"))
                        .content(
                            "{\"type\":\"USER\",\"name\":\""
                                + name
                                + "\",\"bearer_token\":\"bob-secret\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    assertEquals("USER", created.get("type").asText());
    assertTrue(
        created.path("bearer_token").isMissingNode(), "the token must never be echoed back");
    assertEquals(name, adminGet("/principals/" + name.toUpperCase()).get("name").asText());

    mvc.perform(
            bootstrap(post(ADMIN_BASE + "/shares")).content("{\"name\":\"" + unique("s") + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"))
        .andExpect(jsonPath("$.message").value(containsString("may only POST")));
    mvc.perform(bootstrap(get(ADMIN_BASE + "/shares"))).andExpect(status().isForbidden());
    mvc.perform(bootstrap(get(ADMIN_BASE + "/principals"))).andExpect(status().isForbidden());
    mvc.perform(bootstrap(delete(ADMIN_BASE + "/principals/" + name)))
        .andExpect(status().isForbidden());
  }

  @Test
  void reservesPrincipalRegistrationToTheBootstrapToken() throws Exception {
    mvc.perform(
            adminJson(post(ADMIN_BASE + "/principals"))
                .content(
                    "{\"name\":\""
                        + unique("ivan")
                        + "@example.com\",\"bearer_token\":\"ivan-secret\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"))
        .andExpect(
            jsonPath("$.message").value(containsString("only the bootstrap administrator token")));

    mvc.perform(
            post(ADMIN_BASE + "/principals")
                .header("Authorization", "Bearer not-a-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"jane@example.com\",\"bearer_token\":\"jane-secret\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
  }

  @Test
  void registersAPrincipalUnderTheIdTheCallerChose() throws Exception {
    String id = UUID.randomUUID().toString();
    String name = unique("frank") + "@example.com";

    JsonNode created =
        readJson(
            mvc.perform(
                    bootstrap(post(ADMIN_BASE + "/principals"))
                        .content(
                            "{\"id\":\""
                                + id
                                + "\",\"name\":\""
                                + name
                                + "\",\"bearer_token\":\"frank-secret\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    assertEquals(id, created.get("id").asText());
    assertEquals(id, adminGet("/principals/" + name).get("id").asText());
  }

  @Test
  void refusesAnIdThatIsNotAUuidOrIsAlreadyRegistered() throws Exception {
    String taken = adminGet("/principals/" + ALICE).get("id").asText();

    mvc.perform(
            bootstrap(post(ADMIN_BASE + "/principals"))
                .content(
                    "{\"id\":\"not-a-uuid\",\"name\":\""
                        + unique("gina")
                        + "@example.com\",\"bearer_token\":\"gina-secret\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("is not a UUID")));

    mvc.perform(
            bootstrap(post(ADMIN_BASE + "/principals"))
                .content(
                    "{\"id\":\""
                        + taken
                        + "\",\"name\":\""
                        + unique("hugo")
                        + "@example.com\",\"bearer_token\":\"hugo-secret\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_ALREADY_EXISTS"));

    assertEquals(ALICE, adminGet("/principals/" + ALICE).get("name").asText());
  }

  @Test
  void replacingAPrincipalsTokenInvalidatesTheOldOne() throws Exception {
    String name = unique("carol") + "@example.com";
    mvc.perform(
            bootstrap(post(ADMIN_BASE + "/principals"))
                .content("{\"name\":\"" + name + "\",\"bearer_token\":\"carol-first\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            patch(ADMIN_BASE + "/principals/" + name)
                .header("Authorization", "Bearer carol-first")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bearer_token\":\"carol-second\"}"))
        .andExpect(status().isOk());

    mvc.perform(get(ADMIN_BASE + "/principals/" + name).header("Authorization", "Bearer carol-first"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(ADMIN_BASE + "/principals/" + name).header("Authorization", "Bearer carol-second"))
        .andExpect(status().isOk());
  }

  @Test
  void deletesAPrincipalThatOwnsNothing() throws Exception {
    String name = unique("erin") + "@example.com";
    mvc.perform(
            bootstrap(post(ADMIN_BASE + "/principals"))
                .content("{\"name\":\"" + name + "\",\"bearer_token\":\"erin-secret\"}"))
        .andExpect(status().isCreated());

    mvc.perform(adminJson(delete(ADMIN_BASE + "/principals/" + name)))
        .andExpect(status().isNoContent());

    mvc.perform(get(ADMIN_BASE + "/principals/" + name).header("Authorization", "Bearer erin-secret"))
        .andExpect(status().isUnauthorized());
  }
}
