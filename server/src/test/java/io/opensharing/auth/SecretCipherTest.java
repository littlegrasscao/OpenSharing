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
  private static final String ALICE = "941a703c-ff3c-4d6f-8fb8-0e5aca154ed4";
  private static final String BOB = "6b1e0d6c-6a4a-4a2f-9c1f-2d3b4c5d6e7f";

  @Test
  void readsBackWhatItSealed() {
    SecretCipher cipher = cipherWith(KEY);

    assertEquals(SECRET, cipher.decrypt(cipher.encrypt(SECRET, ALICE), ALICE));
  }

  @Test
  void storesSomethingThatLooksNothingLikeTheSecret() {
    String stored = cipherWith(KEY).encrypt(SECRET, ALICE);

    assertTrue(stored.startsWith("v1."), "the stored form says what it is");
    assertFalse(stored.contains(SECRET));
  }

  @Test
  void sealsTheSameSecretDifferentlyEachTime() {
    SecretCipher cipher = cipherWith(KEY);

    assertNotEquals(
        cipher.encrypt(SECRET, ALICE), cipher.encrypt(SECRET, ALICE), "a fresh nonce per encryption");
  }

  /**
   * What a recipient is told when a credential cannot be read, which is all any of these three
   * failures says: why it could not is the deployment's business and goes to the log instead.
   */
  @Test
  void refusesToReadWithADifferentKey() {
    String stored = cipherWith(KEY).encrypt(SECRET, ALICE);

    ApiException failure =
        assertThrows(ApiException.class, () -> cipherWith(base64Key(32, 2)).decrypt(stored, ALICE));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatus());
    assertTrue(failure.getMessage().contains("cannot be served"));
    assertFalse(
        failure.getMessage().contains("key does not decrypt"),
        "which key state a provider is in is not for a recipient to hear");
  }

  @Test
  void refusesToReadSomethingThatWasAltered() {
    String stored = cipherWith(KEY).encrypt(SECRET, ALICE);
    // A byte in the middle, so the change lands on ciphertext and is a change whatever it encoded:
    // rewriting the tail could reproduce the character already there and tamper with nothing.
    char[] altered = stored.toCharArray();
    int middle = altered.length / 2;
    altered[middle] = altered[middle] == 'A' ? 'B' : 'A';

    assertNotEquals(stored, new String(altered), "the test must actually alter something");
    assertThrows(ApiException.class, () -> cipherWith(KEY).decrypt(new String(altered), ALICE));
  }

  @Test
  void refusesToReadAFormItDoesNotKnow() {
    SecretCipher cipher = cipherWith(KEY);

    // What a row written before this scheme existed looks like, and the reason for the version tag.
    assertThrows(ApiException.class, () -> cipher.decrypt("dapi-a-bare-token", ALICE));
    // And a sealed value too short to hold even the nonce, which is a length check, not a tag one.
    assertThrows(ApiException.class, () -> cipher.decrypt("v1.QUJD", ALICE));
  }

  @Test
  void readsNothingBackWithoutAKey() {
    ApiException failure =
        assertThrows(ApiException.class, () -> cipherWith(null).decrypt("v1.anything", ALICE));

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

    ApiException failure = assertThrows(ApiException.class, () -> cipher.encrypt(SECRET, ALICE));
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

  /**
   * The substitution the sealing exists to stop: whoever can write the table copies one provider's
   * credential into another's row, and every read through the second provider's shares would then be
   * made to the catalog with the first one's privileges. It does not decrypt there.
   */
  @Test
  void refusesToReadASecretSealedToSomebodyElse() {
    SecretCipher cipher = cipherWith(KEY);
    String alices = cipher.encrypt(SECRET, ALICE);

    assertThrows(ApiException.class, () -> cipher.decrypt(alices, BOB));
    assertEquals(SECRET, cipher.decrypt(alices, ALICE), "and still reads in the row it belongs to");
  }

  @Test
  void sealsToSomebody() {
    SecretCipher cipher = cipherWith(KEY);

    assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(SECRET, " "));
    assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(SECRET, null));
  }

  /** A token the length of a JWT, which is what a catalog credential often is. */
  @Test
  void sealsATokenAsLongAsARealOne() {
    String jwt = "ey" + "A".repeat(2046);
    SecretCipher cipher = cipherWith(KEY);

    String stored = cipher.encrypt(jwt, ALICE);

    assertEquals(jwt, cipher.decrypt(stored, ALICE));
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
