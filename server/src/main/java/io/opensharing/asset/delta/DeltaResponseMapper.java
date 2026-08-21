package io.opensharing.asset.delta;

import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.AccessMode;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import io.opensharing.protocol.ChangeFileAction;
import io.opensharing.protocol.EndStreamAction;
import io.opensharing.protocol.FileAction;
import io.opensharing.protocol.MetadataAction;
import io.opensharing.protocol.ProtocolAction;
import io.opensharing.protocol.TableAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Turns a snapshot of a shared table into the lines of a Delta read response.
 *
 * <p>Two things are deliberately not passed through from the log. The table's own name and
 * description stay behind, because a recipient sees the alias the object was shared as and the log's
 * name would leak the provider's naming. And a table needing a reader newer than the parquet response
 * format can carry is refused rather than flattened into a response that would be read wrongly.
 */
@Component
public class DeltaResponseMapper {

  /** The only reader version the parquet response format can express. */
  private static final int PARQUET_FORMAT_MAX_READER_VERSION = 1;

  private final UrlSigners signers;
  private final Duration urlTtl;

  public DeltaResponseMapper(UrlSigners signers, OpenSharingProperties properties) {
    this.signers = signers;
    this.urlTtl = properties.getDelta().getUrlTtl();
  }

  /** The two lines of a metadata response: what a reader must support, and what the table is. */
  public List<TableAction> metadata(
      SharedDataObjectEntity object, DeltaTable table, boolean statedVersion) {
    requireParquetReadable(table);
    return List.of(
        TableAction.of(new ProtocolAction(table.snapshot().protocol().minReaderVersion())),
        TableAction.of(metadataAction(object, table, statedVersion, null, null)));
  }

  /**
   * A query response: the same two lines, then one per file the recipient may read.
   *
   * @param statedVersion whether the client asked for a particular version, which is when the
   *     protocol wants the version stamped onto the response
   */
  public List<TableAction> query(
      SharedDataObjectEntity object,
      DeltaTable table,
      boolean statedVersion,
      DeltaSharingCapabilities capabilities) {
    requireParquetReadable(table);
    List<DeltaSnapshot.File> files = table.snapshot().files();
    List<FileAction> fileActions = new ArrayList<>(files.size());
    long totalSize = 0;
    for (DeltaSnapshot.File file : files) {
      fileActions.add(fileAction(table, file, statedVersion));
      totalSize += file.size();
    }

    List<TableAction> actions = new ArrayList<>(fileActions.size() + 3);
    actions.add(TableAction.of(new ProtocolAction(table.snapshot().protocol().minReaderVersion())));
    actions.add(
        TableAction.of(
            metadataAction(object, table, statedVersion, totalSize, (long) files.size())));
    fileActions.forEach(file -> actions.add(TableAction.of(file)));
    if (capabilities.includeEndStreamAction()) {
      actions.add(TableAction.of(endStream(fileActions)));
    }
    return actions;
  }

  /**
   * A change feed response: what a reader must support, the schema the changes are shaped by, then a
   * line per change file in commit order.
   */
  public List<TableAction> changes(
      SharedDataObjectEntity object,
      DeltaTable table,
      DeltaChanges changes,
      DeltaSharingCapabilities capabilities) {
    requireParquetReadable(table);
    List<TableAction> actions = new ArrayList<>(changes.changes().size() + 3);
    actions.add(
        TableAction.of(new ProtocolAction(table.snapshot().protocol().minReaderVersion())));
    actions.add(TableAction.of(metadataAction(object, table, true, null, null)));
    Long earliestExpiry = null;
    for (DeltaChanges.Change change : changes.changes()) {
      SignedUrl signed = signers.sign(change.path(), table.credentials(), urlTtl);
      long expiry = signed.expiration().toEpochMilli();
      earliestExpiry = earliestExpiry == null ? expiry : Math.min(earliestExpiry, expiry);
      ChangeFileAction action =
          new ChangeFileAction(
              signed.url(),
              fileId(table, change.path()),
              change.partitionValues(),
              change.size(),
              change.timestamp(),
              change.version(),
              change.stats(),
              expiry);
      actions.add(
          switch (change.kind()) {
            case ADD -> TableAction.added(action);
            case CDF -> TableAction.changed(action);
            case REMOVE -> TableAction.removed(action);
          });
    }
    if (capabilities.includeEndStreamAction()) {
      actions.add(TableAction.of(new EndStreamAction(null, null, earliestExpiry)));
    }
    return actions;
  }

  private MetadataAction metadataAction(
      SharedDataObjectEntity object,
      DeltaTable table,
      boolean statedVersion,
      Long size,
      Long numFiles) {
    DeltaSnapshot.Metadata metadata = table.snapshot().metadata();
    return new MetadataAction(
        metadata.id(),
        null,
        null,
        object.getStorageLocation(),
        object.getAuxiliaryLocations().isEmpty() ? null : List.copyOf(object.getAuxiliaryLocations()),
        accessModes(object),
        new MetadataAction.Format(metadata.formatProvider(), metadata.formatOptions()),
        metadata.schemaString(),
        metadata.partitionColumns(),
        metadata.configuration(),
        statedVersion ? table.snapshot().version() : null,
        size,
        numFiles);
  }

  private FileAction fileAction(DeltaTable table, DeltaSnapshot.File file, boolean statedVersion) {
    SignedUrl signed = signers.sign(file.path(), table.credentials(), urlTtl);
    return new FileAction(
        signed.url(),
        fileId(table, file.path()),
        file.partitionValues(),
        file.size(),
        file.stats(),
        statedVersion ? table.snapshot().version() : null,
        statedVersion ? table.snapshot().timestamp() : null,
        signed.expiration().toEpochMilli());
  }

  /**
   * A file's id must be the same in every response so a client can cache bytes against it. It is
   * derived from the path relative to the table root, so moving a table keeps the ids it had.
   */
  private static String fileId(DeltaTable table, String path) {
    String root = DeltaLogReader.hadoopPath(table.resolved().storageLocation());
    String relative = path.startsWith(root) ? path.substring(root.length()) : path;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(relative.replaceFirst("^/", "").getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required to derive file ids", e);
    }
  }

  private static EndStreamAction endStream(List<FileAction> files) {
    Long earliest =
        files.stream()
            .map(FileAction::expirationTimestamp)
            .filter(Objects::nonNull)
            .min(Long::compareTo)
            .orElse(null);
    return new EndStreamAction(null, null, earliest);
  }

  private static List<String> accessModes(SharedDataObjectEntity object) {
    if (object.getAccessModes().isEmpty()) {
      return null;
    }
    return object.getAccessModes().stream().map(AccessMode::wireName).sorted().toList();
  }

  private static void requireParquetReadable(DeltaTable table) {
    int minReaderVersion = table.snapshot().protocol().minReaderVersion();
    if (minReaderVersion > PARQUET_FORMAT_MAX_READER_VERSION) {
      throw ApiException.notImplemented(
          "this table needs reader version "
              + minReaderVersion
              + ", which only the delta response format can carry; this server answers in parquet "
              + "format, so read the table through dir access mode instead");
    }
  }
}
