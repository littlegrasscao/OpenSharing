package io.opensharing.catalog;

/**
 * The catalog rejected a request on authorization grounds: either the caller may not use the asset,
 * or the credential presented to the catalog was not accepted.
 */
public class CatalogAuthorizationException extends CatalogException {

  public enum Reason {
    ACCESS_DENIED,
    AUTHENTICATION_FAILED
  }

  private final Reason reason;

  private CatalogAuthorizationException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public static CatalogAuthorizationException accessDenied(
      AssetLookup lookup, CatalogCaller caller) {
    return new CatalogAuthorizationException(
        Reason.ACCESS_DENIED,
        "'"
            + caller.name()
            + "' may not share "
            + lookup.type()
            + " '"
            + lookup.identifier()
            + "'",
        null);
  }

  public static CatalogAuthorizationException authenticationFailed(
      String message, Throwable cause) {
    return new CatalogAuthorizationException(Reason.AUTHENTICATION_FAILED, message, cause);
  }
}
