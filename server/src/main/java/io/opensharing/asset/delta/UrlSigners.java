package io.opensharing.asset.delta;

import io.opensharing.catalog.StorageCredentials;
import io.opensharing.http.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Picks the signer for a path, and says plainly when no signer can serve one. */
@Component
public class UrlSigners {

  private final Map<String, UrlSigner> byScheme = new HashMap<>();

  public UrlSigners(List<UrlSigner> signers) {
    for (UrlSigner signer : signers) {
      signer.schemes().forEach(scheme -> byScheme.put(scheme.toLowerCase(Locale.ROOT), signer));
    }
  }

  public SignedUrl sign(String path, StorageCredentials credentials, Duration ttl) {
    return signerFor(path).sign(path, credentials, capped(credentials, ttl));
  }

  private UrlSigner signerFor(String path) {
    String scheme = scheme(path);
    UrlSigner signer = byScheme.get(scheme);
    if (signer == null) {
      throw ApiException.notImplemented(
          "this build cannot sign urls for '"
              + scheme
              + "' storage; use dir access mode and temporary-table-credentials to read the table");
    }
    return signer;
  }

  private static String scheme(String path) {
    int separator = path.indexOf("://");
    // A bare path is a local one, which is how a table on disk is addressed.
    return separator < 0 ? "file" : path.substring(0, separator).toLowerCase(Locale.ROOT);
  }

  /**
   * A url must not outlive the grant it was derived from, whatever ttl was asked for: the credentials
   * stop working at their expiry and the url would only 403 after it.
   */
  private static Duration capped(StorageCredentials credentials, Duration ttl) {
    if (credentials == null || credentials.expiration() == null) {
      return ttl;
    }
    Duration untilExpiry = Duration.between(Instant.now(), credentials.expiration());
    return untilExpiry.isNegative() || untilExpiry.compareTo(ttl) > 0 ? ttl : untilExpiry;
  }
}
