package io.opensharing.principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opensharing.auth.SecretCipher;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PrincipalStoreTest {

  private static final String KEY = "c2hhcmluZy10ZXN0LWtleS0zMi1ieXRlcy1sb25nISE=";

  @Test
  void refusesATokenTooLongToStore() {
    PrincipalStore store = store();

    ApiException failure =
        assertThrows(
            ApiException.class,
            () ->
                store.provision(
                    PrincipalType.USER, "long@example.com", "x".repeat(2049)));

    assertTrue(failure.getMessage().contains("at most 2048"));
  }

  private static PrincipalStore store() {
    OpenSharingProperties properties = new OpenSharingProperties();
    properties.getSecurity().setCredentialEncryptionKey(KEY);
    return new PrincipalStore(
        Mockito.mock(PrincipalRepository.class),
        Mockito.mock(EntityManager.class),
        new SecretCipher(properties));
  }
}
