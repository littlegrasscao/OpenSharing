package io.opensharing.principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opensharing.auth.CatalogAuthType;
import io.opensharing.auth.SecretCipher;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({PrincipalStore.class, SecretCipher.class})
@EnableConfigurationProperties(OpenSharingProperties.class)
@TestPropertySource(
    properties = {
      "opensharing.security.credential-encryption-key=c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE="
    })
class PrincipalStoreTest {

  @Autowired private PrincipalStore store;

  @Test
  void provisionsANewPrincipalWithTypeAndAuthType() {
    PrincipalEntity created =
        store.provision(
            "11111111-1111-1111-1111-111111111111",
            PrincipalType.GROUP,
            CatalogAuthType.TOKEN,
            "data-team",
            "team-secret");

    assertEquals(PrincipalType.GROUP, created.getType());
    assertEquals(CatalogAuthType.TOKEN, created.getAuthType());
    assertEquals("data-team", created.getName());
    assertEquals(
        "11111111-1111-1111-1111-111111111111",
        store.require("data-team").getId());
    assertTrue(store.findByToken("team-secret").isPresent());
  }

  @Test
  void updatesTypeAndAuthTypeWithoutRotatingAnUnchangedToken() {
    store.provision(
        null, PrincipalType.USER, CatalogAuthType.TOKEN, "alice@example.com", "alice-secret");

    PrincipalEntity updated =
        store.provision(
            null,
            PrincipalType.GROUP,
            CatalogAuthType.TOKEN,
            "alice@example.com",
            "alice-secret");

    assertEquals(PrincipalType.GROUP, updated.getType());
    assertEquals(CatalogAuthType.TOKEN, updated.getAuthType());
    assertTrue(store.findByToken("alice-secret").isPresent());
  }

  @Test
  void rotatesTokenWhenProvisionedWithANewSecret() {
    store.provision(
        null, PrincipalType.USER, CatalogAuthType.TOKEN, "bob@example.com", "old-secret");

    store.provision(
        null, PrincipalType.USER, CatalogAuthType.TOKEN, "bob@example.com", "new-secret");

    assertTrue(store.findByToken("old-secret").isEmpty());
    assertTrue(store.findByToken("new-secret").isPresent());
  }

  @Test
  void registersUnderACallerChosenId() {
    String id = UUID.randomUUID().toString();

    PrincipalEntity created =
        store.create(id, PrincipalType.USER, CatalogAuthType.TOKEN, "carol@example.com", "carol-secret");

    assertEquals(id, created.getId());
    assertEquals(id, store.require("carol@example.com").getId());
  }

  @Test
  void rejectsDuplicateNamesAndInvalidIds() {
    store.create(
        null, PrincipalType.USER, CatalogAuthType.TOKEN, "dave@example.com", "dave-secret");

    ApiException duplicateName =
        assertThrows(
            ApiException.class,
            () ->
                store.create(
                    null,
                    PrincipalType.USER,
                    CatalogAuthType.TOKEN,
                    "Dave@Example.com",
                    "other-secret"));
    assertTrue(duplicateName.getMessage().contains("already exists"));

    ApiException badId =
        assertThrows(
            ApiException.class,
            () ->
                store.create(
                    "not-a-uuid",
                    PrincipalType.USER,
                    CatalogAuthType.TOKEN,
                    "eve@example.com",
                    "eve-secret"));
    assertTrue(badId.getMessage().contains("is not a UUID"));
  }

  @Test
  void refusesOidcAuthType() {
    ApiException failure =
        assertThrows(
            ApiException.class,
            () ->
                store.provision(
                    null,
                    PrincipalType.USER,
                    CatalogAuthType.OIDC,
                    "oidc@example.com",
                    "unused"));

    assertTrue(failure.getMessage().contains("OIDC is not implemented yet"));
  }

  @Test
  void refusesATokenTooLongToStore() {
    ApiException failure =
        assertThrows(
            ApiException.class,
            () ->
                store.provision(
                    null,
                    PrincipalType.USER,
                    CatalogAuthType.TOKEN,
                    "long@example.com",
                    "x".repeat(2049)));

    assertTrue(failure.getMessage().contains("at most 2048"));
  }
}
