package io.opensharing.catalog.unity;

import io.opensharing.principal.Caller;
import io.opensharing.runtime.ProviderIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Resolves a provider-admin request's caller by asking Unity Catalog whose token this is, instead
 * of maintaining a parallel {@code opensharing.admin.principals} list or storing a copy of anyone's
 * credential — see {@code UnityCatalogClient#authorize}. One class serves both standalone mode
 * (pointed at a remote Unity Catalog) and embedded mode (pointed at the host's own loopback
 * address): the catalog's new authorize endpoint is a general server capability either way, not
 * something embedding adds.
 *
 * <p>Create and Create Recipient are the only two provider-admin operations Unity Catalog is asked
 * to gate ({@code CREATE_SHARE}, {@code CREATE_RECIPIENT} — see {@link #privilegeFor}); every other
 * request resolves identity only, and this server enforces its own ownership rules on top (a share
 * or recipient may only be changed by the principal that owns it) rather than asking the catalog to
 * decide that again on every call.
 */
public final class UnityCatalogProviderIdentityResolver implements ProviderIdentityResolver {

  private static final String BEARER_PREFIX = "Bearer ";

  private final UnityCatalogClient client;

  public UnityCatalogProviderIdentityResolver(
      URI uri, Duration connectTimeout, Duration requestTimeout) {
    this.client = new UnityCatalogClient(uri, connectTimeout, requestTimeout);
  }

  @Override
  public Optional<Caller> resolve(HttpServletRequest request) {
    String token = bearerToken(request);
    if (token == null) {
      return Optional.empty();
    }
    return client
        .authorize(token, privilegeFor(request))
        .filter(UnityCatalogClient.AuthorizeResult::authorized)
        .map(result -> new Caller(result.userId(), result.userName(), token));
  }

  /**
   * The one privilege this server ever asks the catalog to check on a provider-admin request — the
   * two operations that create a new, otherwise-unowned object, matched on method and path since
   * that is all a filter this far upstream has to go on.
   */
  private static String privilegeFor(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return null;
    }
    String path = request.getRequestURI();
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    if (path.endsWith("/shares")) {
      return "CREATE_SHARE";
    }
    if (path.endsWith("/recipients")) {
      return "CREATE_RECIPIENT";
    }
    return null;
  }

  private static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    return header != null && header.startsWith(BEARER_PREFIX)
        ? header.substring(BEARER_PREFIX.length()).trim()
        : null;
  }
}
