package io.opensharing.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecretsTest {

  @Test
  void hashesDeterministically() {
    assertEquals(Secrets.sha256("hello"), Secrets.sha256("hello"));
    assertNotEquals(Secrets.sha256("hello"), Secrets.sha256("world"));
  }

  @Test
  void mintsPrefixedTokens() {
    String token = Secrets.newToken();
    assertTrue(token.startsWith(Secrets.TOKEN_PREFIX));
    assertNotEquals(token, Secrets.newToken());
  }
}
