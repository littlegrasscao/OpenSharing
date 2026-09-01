package io.opensharing.catalog;

/**
 * Who a catalog request is made on behalf of.
 *
 * <p>A provider admin adding an object to a share is resolved as themselves, so the catalog decides
 * whether that person may share it.
 */
public record CatalogCaller(String name, String bearerToken) {

  public static CatalogCaller of(String name, String bearerToken) {
    return new CatalogCaller(name, bearerToken);
  }
}
