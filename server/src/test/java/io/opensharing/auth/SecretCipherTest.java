package io.opensharing.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The one secret the server can read back, and what happens when it cannot. */
class SecretCipherTest {

  private static final String KEY = base64Key(32, 1);
  private static final String SECRET = "dapi-a-catalog-token";

  @Test
  void readsBackWhatItSealed() {
    SecretCipher cipher = cipherWith(KEY);

    assertEquals(SECRET, cipher.decrypt(cipher.encrypt(SECRET)));
  }

  @Test
  void storesSomethingThatLooksNothingLikeTheSecret() {
    String stored = cipherWith(KEY).encrypt(SECRET);

    assertTrue(stored.startsWith("v1."), "the stored form says what it is");
    assertFalse(stored.contains(SECRET));
  }

  @Test
  void sealsTheSameSecretDifferentlyEachTime() {
    SecretCipher cipher = cipherWith(KEY);

    assertNotEquals(
        cipher.encrypt(SECRET), cipher.encrypt(SECRET), "a fresh nonce per encryption");
  }

  /**
   * What a recipient is told when a credential cannot be read, which is all any of these three
   * failures says: why it could not is the deployment's business and goes to the log instead.
   */
  @Test
  void refusesToReadWithADifferentKey() {
    String stored = cipherWith(KEY).encrypt(SECRET);

    ApiException failure =
        assertThrows(ApiException.class, () -> cipherWith(base64Key(32, 2)).decrypt(stored));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatus());
    assertTrue(failure.getMessage().contains("cannot be served"));
    assertFalse(
        failure.getMessage().contains("key does not decrypt"),
        "which key state a provider is in is not for a recipient to hear");
  }

  @Test
  void refusesToReadSomethingThatWasAltered() {
    String stored = cipherWith(KEY).encrypt(SECRET);
    // A byte in the middle, so the change lands on ciphertext and is a change whatever it encoded:
    // rewriting the tail could reproduce the character already there and tamper with nothing.
    char[] altered = stored.toCharArray();
    int middle = altered.length / 2;
    altered[middle] = altered[middle] == 'A' ? 'B' : 'A';

    assertNotEquals(stored, new String(altered), "the test must actually alter something");
    assertThrows(ApiException.class, () -> cipherWith(KEY).decrypt(new String(altered)));
  }

  @Test
  void refusesToReadAFormItDoesNotKnow() {
    SecretCipher cipher = cipherWith(KEY);

    // What a row written before this scheme existed looks like, and the reason for the version tag.
    assertThrows(ApiException.class, () -> cipher.decrypt("dapi-a-bare-token"));
    // And a sealed value too short to hold even the nonce, which is a length check, not a tag one.
    assertThrows(ApiException.class, () -> cipher.decrypt("v1.QUJD"));
  }

  @Test
  void readsNothingBackWithoutAKey() {
    ApiException failure =
        assertThrows(ApiException.class, () -> cipherWith(null).decrypt("v1.anything"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatus());
  }

  /**
   * A deployment with no key cannot register a principal, and hears that it is the server that is
   * unfit — not that the request was bad, which is what a 4xx would say about a body that was fine
   * and would keep the failure out of whatever watches for 5xx.
   */
  @Test
  void storesNothingWithoutAKey() {
    SecretCipher cipher = cipherWith(null);

    ApiException failure = assertThrows(ApiException.class, () -> cipher.encrypt(SECRET));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatus());
    assertTrue(failure.getMessage().contains("credential-encryption-key"));
  }

  @Test
  void refusesAKeyThatIsNotOne() {
    assertThrows(IllegalStateException.class, () -> cipherWith("not base64 at all!"));
    assertThrows(IllegalStateException.class, () -> cipherWith(base64Key(20, 1)));
  }

  private static SecretCipher cipherWith(String key) {
    OpenSharingProperties properties = new OpenSharingProperties();
    properties.getSecurity().setCredentialEncryptionKey(key);
    return new SecretCipher(properties);
  }

  /** A token the length of a JWT, which is what a catalog credential often is. */
  @Test
  void sealsATokenAsLongAsARealOne() {
    String jwt = "ey" + "A".repeat(2046);
    SecretCipher cipher = cipherWith(KEY);

    String stored = cipher.encrypt(jwt);

    assertEquals(jwt, cipher.decrypt(stored));
    assertTrue(stored.length() < 4096, "and the stored form still fits its column: " + stored.length());
  }

  private static String base64Key(int bytes, int seed) {
    byte[] key = new byte[bytes];
    for (int i = 0; i < bytes; i++) {
      key[i] = (byte) (i * 7 + seed);
    }
    return Base64.getEncoder().encodeToString(key);
  }
}
