package io.opensharing.catalog;

/** The catalog rejected the caller's credential or refused access to the asset. */
public class CatalogAuthorizationException extends CatalogException {

  public CatalogAuthorizationException(String message) {
    super(message);
  }

  public CatalogAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  public static CatalogAuthorizationException accessDenied(
      AssetLookup lookup, CatalogCaller caller) {
    return new CatalogAuthorizationException(
        "'"
            + caller.name()
            + "' may not share "
            + lookup.type()
            + " '"
            + lookup.identifier()
            + "'");
  }
}
