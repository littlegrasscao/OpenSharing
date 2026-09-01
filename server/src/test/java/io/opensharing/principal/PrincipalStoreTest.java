package io.opensharing.principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opensharing.http.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(PrincipalStore.class)
class PrincipalStoreTest {

  @Autowired private PrincipalStore store;

  @Test
  void createsFindsUpdatesAndDeletesPrincipals() {
    PrincipalEntity created =
        store.create(null, PrincipalType.USER, "Alice@Example.com", "alice-secret");
    assertEquals("Alice@Example.com", created.getName());
    assertEquals("alice@example.com", created.getNameLower());

    PrincipalEntity byToken =
        store.findByToken("alice-secret").orElseThrow(() -> new AssertionError("missing"));
    assertEquals(created.getId(), byToken.getId());

    PrincipalEntity renamed = store.update("alice@example.com", "alice@corp.com", null);
    assertEquals("alice@corp.com", renamed.getName());

    store.update("alice@corp.com", null, "alice-new-secret");
    assertTrue(store.findByToken("alice-secret").isEmpty());
    assertTrue(store.findByToken("alice-new-secret").isPresent());

    store.delete("alice@corp.com");
    assertThrows(ApiException.class, () -> store.require("alice@corp.com"));
  }

  @Test
  void registersUnderACallerChosenId() {
    String id = UUID.randomUUID().toString();
    PrincipalEntity created = store.create(id, PrincipalType.USER, "bob@example.com", "bob-secret");
    assertEquals(id, created.getId());
    assertEquals(id, store.require("bob@example.com").getId());
  }

  @Test
  void rejectsDuplicateNamesAndInvalidIds() {
    store.create(null, PrincipalType.USER, "carol@example.com", "carol-secret");

    ApiException duplicateName =
        assertThrows(
            ApiException.class,
            () -> store.create(null, PrincipalType.USER, "Carol@Example.com", "other-secret"));
    assertTrue(duplicateName.getMessage().contains("already exists"));

    ApiException badId =
        assertThrows(
            ApiException.class,
            () -> store.create("not-a-uuid", PrincipalType.USER, "dave@example.com", "dave-secret"));
    assertTrue(badId.getMessage().contains("is not a UUID"));
  }
}
