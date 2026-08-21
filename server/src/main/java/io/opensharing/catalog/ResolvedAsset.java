package io.opensharing.catalog;

import java.util.List;
import java.util.Set;

/**
 * What the catalog knows about an asset: where it physically lives and how it can be read.
 *
 * @param catalogAssetId the catalog's internal id, needed by catalogs that vend credentials by id
 *     rather than by path
 * @param storageLocation where the bytes are, and what credentials get scoped to
 * @param metadataLocation the pointer a client needs to interpret those bytes, such as an Iceberg
 *     table's current metadata JSON. Null for formats that need no such pointer.
 * @param schema the source's schema as the catalog states it, or null if the catalog does not report
 *     one. Not persisted: it is only as current as the resolution that produced it.
 * @param partitionColumns the columns the source is partitioned by, in order, or empty when it is
 *     unpartitioned or the catalog does not say. Unity Catalog reports these through the partition
 *     index on each column; an Iceberg catalog through the partition spec.
 * @param subtype the catalog's own refinement of the type, e.g. {@code VIEW} or
 *     {@code MATERIALIZED_VIEW} for a table
 */
public record ResolvedAsset(
    AssetType type,
    String identifier,
    String catalogAssetId,
    String storageLocation,
    String metadataLocation,
    TableFormat format,
    String schema,
    List<String> partitionColumns,
    String subtype,
    Set<AccessMode> accessModes,
    List<String> auxiliaryLocations) {

  public ResolvedAsset {
    accessModes = accessModes == null ? Set.of() : Set.copyOf(accessModes);
    auxiliaryLocations = auxiliaryLocations == null ? List.of() : List.copyOf(auxiliaryLocations);
    partitionColumns = partitionColumns == null ? List.of() : List.copyOf(partitionColumns);
  }

  public static Builder builder(AssetType type, String identifier) {
    return new Builder(type, identifier);
  }

  public static final class Builder {
    private final AssetType type;
    private final String identifier;
    private String catalogAssetId;
    private String storageLocation;
    private String metadataLocation;
    private TableFormat format;
    private String schema;
    private List<String> partitionColumns = List.of();
    private String subtype;
    private Set<AccessMode> accessModes = Set.of();
    private List<String> auxiliaryLocations = List.of();

    private Builder(AssetType type, String identifier) {
      this.type = type;
      this.identifier = identifier;
    }

    public Builder catalogAssetId(String value) {
      this.catalogAssetId = value;
      return this;
    }

    public Builder storageLocation(String value) {
      this.storageLocation = value;
      return this;
    }

    public Builder metadataLocation(String value) {
      this.metadataLocation = value;
      return this;
    }

    public Builder format(TableFormat value) {
      this.format = value;
      return this;
    }

    public Builder schema(String value) {
      this.schema = value;
      return this;
    }

    public Builder partitionColumns(List<String> value) {
      this.partitionColumns = value;
      return this;
    }

    public Builder subtype(String value) {
      this.subtype = value;
      return this;
    }

    public Builder accessModes(Set<AccessMode> value) {
      this.accessModes = value;
      return this;
    }

    public Builder auxiliaryLocations(List<String> value) {
      this.auxiliaryLocations = value;
      return this;
    }

    public ResolvedAsset build() {
      return new ResolvedAsset(
          type,
          identifier,
          catalogAssetId,
          storageLocation,
          metadataLocation,
          format,
          schema,
          partitionColumns,
          subtype,
          accessModes,
          auxiliaryLocations);
    }
  }
}
