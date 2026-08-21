package io.opensharing.asset;

import io.opensharing.catalog.AccessMode;
import io.opensharing.catalog.AssetAccessDeniedException;
import io.opensharing.catalog.AssetAction;
import io.opensharing.catalog.AssetLookup;
import io.opensharing.catalog.AssetNotFoundException;
import io.opensharing.catalog.AssetType;
import io.opensharing.catalog.CatalogCaller;
import io.opensharing.catalog.CatalogConnector;
import io.opensharing.catalog.CatalogException;
import io.opensharing.catalog.CredentialRequest;
import io.opensharing.catalog.ResolvedAsset;
import io.opensharing.catalog.StorageCredentials;
import io.opensharing.catalog.TableFormat;
import io.opensharing.catalog.UnsupportedAssetTypeException;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import io.opensharing.http.ErrorCodes;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Bridges stored objects and the catalog. The store keeps a snapshot of what the catalog last
 * reported so listings are cheap, but anything that hands out access re-resolves first, so a
 * relocated or dropped source is never served from stale state.
 */
@Service
public class AssetResolutionService {

  private final CatalogConnector catalog;
  private final SharedDataObjectStore objects;
  private final OpenSharingProperties properties;

  public AssetResolutionService(
      CatalogConnector catalog,
      SharedDataObjectStore objects,
      OpenSharingProperties properties) {
    this.catalog = catalog;
    this.objects = objects;
    this.properties = properties;
  }

  /**
   * Resolves an object a provider admin is about to share, as the admin themselves, so the catalog
   * decides whether they may share it. A missing object is the admin's mistake, so it is reported as a
   * bad request rather than as a missing sharing-server resource.
   */
  public ResolvedAsset resolveForRegistration(
      AssetType type, String catalogName, CatalogCaller caller) {
    try {
      return catalog.resolveAsset(AssetLookup.of(type, catalogName), caller, AssetAction.SHARE);
    } catch (AssetNotFoundException e) {
      throw ApiException.invalidParameter(
          type + " '" + catalogName + "' does not exist in the " + catalog.name() + " catalog");
    }
  }

  /**
   * Re-resolves a shared object and refreshes its stored snapshot when the catalog has moved it. A
   * source that has gone away, or that the sharing server may no longer read, is recorded on the
   * object so it stops being listed instead of failing every request from now on.
   */
  public ResolvedAsset resolveForServing(SharedDataObjectEntity object) {
    AssetLookup lookup = AssetLookup.of(object.getType(), object.getName());
    ResolvedAsset resolved;
    try {
      resolved = catalog.resolveAsset(lookup, CatalogCaller.server(), AssetAction.READ);
    } catch (AssetNotFoundException e) {
      throw unservable(
          object,
          SharedObjectStatus.SOURCE_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ErrorCodes.RESOURCE_DOES_NOT_EXIST,
          "'" + object.getName() + "' no longer exists in the " + catalog.name() + " catalog");
    } catch (AssetAccessDeniedException e) {
      throw unservable(
          object,
          SharedObjectStatus.PERMISSION_DENIED,
          HttpStatus.FORBIDDEN,
          ErrorCodes.PERMISSION_DENIED,
          "the sharing server may no longer read '" + object.getName() + "'");
    }
    if (snapshotChanged(object, resolved)) {
      applySnapshot(object, resolved);
      persistIfStored(object);
    }
    return resolved;
  }

  /**
   * A table reached through a shared schema has no row to refresh, and wants none: it was assembled
   * from this very resolution, so there is nothing to bring up to date.
   */
  private void persistIfStored(SharedDataObjectEntity object) {
    if (!object.isInSharedSchema()) {
      objects.save(object);
    }
  }

  /**
   * A schema may only be shared if the catalog can say what it contains, since that is the only place
   * a recipient's table list can come from. Asked while the provider is still on the phone, so they
   * hear about it now rather than a recipient hearing about it later.
   */
  public void requireEnumerable(ResolvedAsset schema, CatalogCaller caller) {
    try {
      catalog.listChildren(AssetLookup.of(schema.type(), schema.identifier()), caller);
    } catch (UnsupportedAssetTypeException e) {
      throw ApiException.invalidParameter(
          "the "
              + catalog.name()
              + " catalog cannot list what is in '"
              + schema.identifier()
              + "', so its schemas cannot be shared as a whole; share their tables individually");
    }
  }

  /**
   * The tables a shared schema holds right now. Asked on every listing rather than remembered, which
   * is the point of sharing a schema: a table added to it today is shared today, and one dropped
   * stops being offered without the provider doing anything.
   */
  public List<ResolvedAsset> listSharedSchemaTables(SharedDataObjectEntity schemaGrant) {
    AssetLookup lookup = AssetLookup.of(schemaGrant.getType(), schemaGrant.getName());
    try {
      return catalog.listChildren(lookup, CatalogCaller.server()).stream()
          .filter(child -> child.type() == AssetType.TABLE)
          .toList();
    } catch (AssetNotFoundException e) {
      throw unservable(
          schemaGrant,
          SharedObjectStatus.SOURCE_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ErrorCodes.RESOURCE_DOES_NOT_EXIST,
          "'" + schemaGrant.getName() + "' no longer exists in the " + catalog.name() + " catalog");
    } catch (AssetAccessDeniedException e) {
      throw unservable(
          schemaGrant,
          SharedObjectStatus.PERMISSION_DENIED,
          HttpStatus.FORBIDDEN,
          ErrorCodes.PERMISSION_DENIED,
          "the sharing server may no longer read '" + schemaGrant.getName() + "'");
    }
  }

  /**
   * Asks the catalog to mint credentials for an object's location. A catalog may answer with one
   * entry per prefix the asset spans, so the one covering the location being read is selected here,
   * longest prefix first, since a catalog can return both a bucket-wide and a table-specific grant.
   */
  public StorageCredentials vendCredentials(CredentialRequest request) {
    List<StorageCredentials> minted = catalog.getStorageCredentials(request);
    if (minted == null || minted.isEmpty()) {
      throw new CatalogException(
          "the " + catalog.name() + " catalog vended no credentials for '" + request.identifier() + "'");
    }
    return minted.stream()
        .filter(credentials -> covers(credentials, request.storageLocation()))
        .max(Comparator.comparingInt(credentials -> credentials.prefix().length()))
        .orElseThrow(
            () ->
                new CatalogException(
                    "the "
                        + catalog.name()
                        + " catalog vended credentials for "
                        + minted.stream().map(StorageCredentials::prefix).toList()
                        + ", none covering '"
                        + request.storageLocation()
                        + "'"));
  }

  private static boolean covers(StorageCredentials credentials, String location) {
    String prefix = credentials.prefix();
    return prefix == null || prefix.isBlank() || location.startsWith(prefix);
  }

  /** Copies catalog state onto the stored object, which also revives one that had stopped resolving. */
  public void applySnapshot(SharedDataObjectEntity object, ResolvedAsset resolved) {
    object.setSourceAssetId(resolved.catalogAssetId());
    object.setStorageLocation(resolved.storageLocation());
    object.setSourceFormat(resolved.format());
    object.setSourceSubtype(resolved.subtype());
    object.setAccessModes(accessModesFor(resolved));
    object.setAuxiliaryLocations(resolved.auxiliaryLocations());
    object.setStatus(SharedObjectStatus.ACTIVE);
  }

  /**
   * An object is readable as a directory whenever the catalog can scope credentials to its location,
   * and by url only if this server can actually serve one: a Delta table, with log reading turned on.
   * The protocol lets a client pick a mode from this list, so advertising one that would then answer
   * {@code NOT_IMPLEMENTED} would send it down a road with no end.
   */
  private Set<AccessMode> accessModesFor(ResolvedAsset resolved) {
    Set<AccessMode> modes = new LinkedHashSet<>(resolved.accessModes());
    if (resolved.storageLocation() != null && !resolved.storageLocation().isBlank()) {
      modes.add(AccessMode.DIR);
    }
    if (properties.getDelta().isUrlAccessEnabled() && resolved.format() == TableFormat.DELTA) {
      modes.add(AccessMode.URL);
    } else {
      modes.remove(AccessMode.URL);
    }
    return modes;
  }

  private ApiException unservable(
      SharedDataObjectEntity object,
      SharedObjectStatus status,
      HttpStatus httpStatus,
      String errorCode,
      String message) {
    if (object.getStatus() != status) {
      object.setStatus(status);
      persistIfStored(object);
    }
    return new ApiException(httpStatus, errorCode, message);
  }

  private boolean snapshotChanged(SharedDataObjectEntity object, ResolvedAsset resolved) {
    return object.getStatus() != SharedObjectStatus.ACTIVE
        || !Objects.equals(object.getStorageLocation(), resolved.storageLocation())
        || !Objects.equals(object.getSourceAssetId(), resolved.catalogAssetId())
        || !Objects.equals(object.getSourceSubtype(), resolved.subtype())
        || object.getSourceFormat() != resolved.format()
        || !object.getAccessModes().equals(accessModesFor(resolved))
        || !object.getAuxiliaryLocations().equals(resolved.auxiliaryLocations());
  }
}
