package io.opensharing.auth;

import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import io.opensharing.http.ErrorCodes;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The one secret this server keeps in a form it can read back.
 *
 * <p>Everything a principal or a recipient authenticates with is stored as a hash, because the server
 * only ever needs to recognize a secret someone just presented — see {@link Secrets}. A principal's
 * token is the exception, and not because it authenticates them: it is also their catalog credential,
 * which the server has to present to the catalog on a recipient's request, long after they have gone.
 * Recognizing is not enough for that, so a second copy is encrypted rather than hashed, at the cost a
 * hash does not have: whoever holds both the database and the key holds every provider's credential,
 * and can act as them here as well. That is why the key is configuration,
 * meant to come from somewhere a database dump does not — {@code
 * opensharing.security.credential-encryption-key}, an environment variable, a mounted secret, a KMS.
 *
 * <p>AES-GCM, so a ciphertext cannot be altered undetected, with a fresh random nonce per encryption
 * and the nonce stored alongside. The stored form is versioned, so a later change of algorithm can
 * tell what it is reading rather than guessing.
 */
@Component
public class SecretCipher {

  private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

  /** Prefix of the stored form, so its shape is recognizable and can change later. */
  private static final String VERSION = "v1.";

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private static final SecureRandom RANDOM = new SecureRandom();

  /** Null when the deployment configured no key, which leaves credential storage unavailable. */
  private final SecretKeySpec key;

  public SecretCipher(OpenSharingProperties properties) {
    this.key = keyFrom(properties.getSecurity().getCredentialEncryptionKey());
  }

  /**
   * The stored form of a secret: version, nonce and ciphertext, base64 in one string.
   *
   * <p>Only registering a principal reaches this, so the answer is written for the administrator
   * doing it — and reported as this server being unfit rather than their request being wrong, because
   * nothing they could send would work until the deployment has a key.
   */
  public String encrypt(String plaintext) {
    if (key == null) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCodes.INTERNAL_ERROR,
          "this server cannot store a secret it has to present later because it has no encryption "
              + "key; set opensharing.security.credential-encryption-key to a base64 AES key. Until "
              + "then no principal can be registered, and so nothing can be shared or served");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] stored = new byte[nonce.length + sealed.length];
      System.arraycopy(nonce, 0, stored, 0, nonce.length);
      System.arraycopy(sealed, 0, stored, nonce.length, sealed.length);
      return VERSION + Base64.getEncoder().encodeToString(stored);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM is required but unavailable", e);
    }
  }

  /**
   * Reads a stored secret back.
   *
   * <p>Failure here is the deployment's, not the caller's: the key has been changed or lost, or the
   * stored bytes were altered, and no request can recover from any of them. It is reported as this
   * server failing rather than as the catalog refusing, so that the thing to go and fix is named.
   */
  public String decrypt(String stored) {
    if (key == null) {
      throw failed("no encryption key is configured", null);
    }
    if (!stored.startsWith(VERSION)) {
      throw failed("the stored form is not one this version can read", null);
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(stored.substring(VERSION.length()));
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
      return new String(
          cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw failed("the configured key does not decrypt it", e);
    }
  }

  /**
   * Only a recipient's read reaches this, so which of the three went wrong — and the cause that says
   * a rotated key apart from an altered row — goes to whoever runs the server. What a recipient gets
   * says that the table cannot be served and nothing about the state of a provider's key.
   */
  private static ApiException failed(String why, Exception cause) {
    log.error("A stored catalog credential could not be read: {}", why, cause);
    return new ApiException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCodes.INTERNAL_ERROR,
        "a stored catalog credential could not be read, so this table cannot be served");
  }

  /**
   * A blank setting means the feature is off, while a present one that is not a key is a deployment
   * that meant to have it and does not, so that fails at startup rather than on the first read.
   */
  private static SecretKeySpec keyFrom(String configured) {
    if (configured == null || configured.isBlank()) {
      return null;
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(configured.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "opensharing.security.credential-encryption-key is not valid base64");
    }
    if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
      throw new IllegalStateException(
          "opensharing.security.credential-encryption-key decodes to "
              + bytes.length
              + " bytes; an AES key is 16, 24 or 32");
    }
    return new SecretKeySpec(bytes, "AES");
  }
}
