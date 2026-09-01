package io.opensharing.catalog;

/** Raised when the catalog cannot satisfy a request. */
public class CatalogException extends RuntimeException {

  public enum Kind {
    ACCESS_DENIED,
    AUTHENTICATION_FAILED
  }

  private final Kind kind;

  public CatalogException(String message) {
    this(message, null, null);
  }

  public CatalogException(String message, Throwable cause) {
    this(message, cause, null);
  }

  private CatalogException(String message, Throwable cause, Kind kind) {
    super(message, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public static CatalogException accessDenied(AssetLookup lookup, CatalogCaller caller) {
    return new CatalogException(
        "'"
            + caller.name()
            + "' may not share "
            + lookup.type()
            + " '"
            + lookup.identifier()
            + "'",
        null,
        Kind.ACCESS_DENIED);
  }

  public static CatalogException authenticationFailed(String message, Throwable cause) {
    return new CatalogException(message, cause, Kind.AUTHENTICATION_FAILED);
  }
}
