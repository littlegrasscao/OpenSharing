package io.opensharing.catalog;

/**
 * Who a catalog request is made on behalf of.
 *
 * <p>A provider admin adding an object to a share is resolved as themselves, so the catalog decides
 * whether that person may share it. Requests with no user behind them — a recipient reading a table
 * long after it was shared — use {@link #server()}, because the server keeps only a hash of a
 * principal's token and cannot replay it later.
 */
public record CatalogCaller(String name, String bearerToken) {

  private static final CatalogCaller SERVER = new CatalogCaller(null, null);

  public static CatalogCaller of(String name, String bearerToken) {
    return new CatalogCaller(name, bearerToken);
  }

  /** The sharing server's own catalog identity. */
  public static CatalogCaller server() {
    return SERVER;
  }

  public boolean isServer() {
    return name == null;
  }
}
