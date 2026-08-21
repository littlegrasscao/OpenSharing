package io.opensharing.catalog;

import java.util.List;

/**
 * The seam between the sharing server and the system of record for assets. The server never talks
 * to cloud storage itself: it asks the catalog where an asset lives and asks it to mint scoped,
 * TTL-bounded credentials the recipient uses to read bytes directly.
 *
 * <p>Two operations carry the whole integration, and each has a counterpart in every catalog worth
 * plugging in: Unity Catalog answers them with {@code GET /tables/{full_name}} and {@code POST
 * /temporary-table-credentials}, an Iceberg REST catalog such as Polaris with {@code loadTable} and
 * {@code loadCredentials}. Nothing else is required of an implementation, so that the cost of adding
 * one stays close to those two calls. {@link #listChildren} is offered on top for a catalog that can
 * enumerate, and is what a provider needs to share a whole schema rather than a list of tables.
 *
 * <p>Implementations authenticate to the catalog as a service principal and are expected to be
 * thread-safe.
 */
public interface CatalogConnector {

  /** Identifier used to select this connector in configuration. */
  String name();

  /**
   * Resolves an asset to its physical location, as a given caller and for a stated purpose, so that
   * a catalog owning authorization can refuse. Called when an asset is added to a share and again
   * when credentials are vended, so a relocation is picked up rather than served from a stale
   * snapshot.
   *
   * <p>This doubles as the existence check. There is no separate one, because every caller that
   * wants to know whether an asset exists also wants the metadata that proves it.
   *
   * @param caller the principal the request is made for, or {@link CatalogCaller#server()}
   * @param intent what the caller means to do with the asset
   * @throws AssetNotFoundException if the catalog has no such asset
   * @throws AssetAccessDeniedException if the caller may not do this with the asset
   * @throws CatalogAuthenticationException if the catalog rejects the connector's own credentials
   * @throws CatalogException if the catalog cannot be reached or refuses the request
   */
  ResolvedAsset resolveAsset(AssetLookup lookup, CatalogCaller caller, AssetAction intent);

  /**
   * Lists the assets a container holds, which is what makes sharing a whole schema possible: the
   * grant names the schema, and its contents are whatever the catalog says they are at the moment
   * someone asks.
   *
   * <p>Optional, and the only operation that is. A catalog that cannot enumerate is still a perfectly
   * good catalog for sharing individual assets, so this refuses rather than forcing every
   * implementation to answer; adding a schema to a share is then rejected up front instead of failing
   * later, when a recipient tries to list it.
   *
   * <p>Full {@link ResolvedAsset}s are returned rather than bare identifiers because the catalogs
   * worth plugging in already carry the metadata in their list response — Unity Catalog's
   * {@code GET /tables?schema_name=} answers with each table's columns and location — and asking for
   * identifiers alone would throw that away and buy it back one request per child.
   *
   * @param parent the container to enumerate, such as a {@link AssetType#SCHEMA}
   * @throws UnsupportedAssetTypeException if this catalog cannot enumerate that kind of container
   * @throws AssetNotFoundException if the container itself does not exist
   */
  default List<ResolvedAsset> listChildren(AssetLookup parent, CatalogCaller caller) {
    throw new UnsupportedAssetTypeException(
        "the " + name() + " catalog cannot list the contents of a " + parent.type());
  }

  /**
   * Mints credentials scoped to the asset's storage location.
   *
   * <p>Returns one entry per storage prefix the asset spans, mirroring the Iceberg REST
   * {@code LoadCredentialsResponse}; a catalog that scopes to a single prefix returns a single
   * element. The caller picks the entry covering the location it is about to read.
   *
   * @throws CatalogException if the catalog refuses or cannot mint credentials
   */
  List<StorageCredentials> getStorageCredentials(CredentialRequest request);
}
