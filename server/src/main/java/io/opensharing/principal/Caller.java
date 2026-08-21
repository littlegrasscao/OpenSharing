package io.opensharing.principal;

/**
 * The principal behind a provider-admin request, resolved from the bearer token it presented.
 *
 * <p>The token travels with the caller because the server queries the catalog as them: it holds only
 * a hash at rest, so the request that presents the plaintext is the only chance to use it.
 */
public record Caller(String principalId, String name, String bearerToken) {

  public static final String REQUEST_ATTRIBUTE = "io.opensharing.caller";

  public static Caller of(PrincipalEntity principal, String bearerToken) {
    return new Caller(principal.getId(), principal.getName(), bearerToken);
  }
}
