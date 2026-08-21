package io.opensharing.asset.delta;

import io.delta.kernel.Scan;
import io.delta.kernel.ScanBuilder;
import io.delta.kernel.Snapshot;
import io.delta.kernel.Table;
import io.delta.kernel.data.ColumnarBatch;
import io.delta.kernel.data.FilteredColumnarBatch;
import io.delta.kernel.data.Row;
import io.delta.kernel.defaults.engine.DefaultEngine;
import io.delta.kernel.engine.Engine;
import io.delta.kernel.exceptions.KernelEngineException;
import io.delta.kernel.exceptions.KernelException;
import io.delta.kernel.exceptions.TableNotFoundException;
import io.delta.kernel.internal.DeltaLogActionUtils.DeltaAction;
import io.delta.kernel.internal.InternalScanFileUtils;
import io.delta.kernel.internal.ScanImpl;
import io.delta.kernel.internal.SnapshotImpl;
import io.delta.kernel.internal.TableImpl;
import io.delta.kernel.internal.actions.Format;
import io.delta.kernel.internal.util.VectorUtils;
import io.delta.kernel.utils.CloseableIterator;
import io.delta.kernel.utils.FileStatus;
import io.opensharing.catalog.CloudProvider;
import io.opensharing.catalog.StorageCredentialKeys;
import io.opensharing.catalog.StorageCredentials;
import io.opensharing.http.ApiException;
import io.opensharing.http.ErrorCodes;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reads a shared table's Delta log, using credentials the catalog minted for that table's location.
 *
 * <p>This is the one place in the server that touches the storage a table lives in, and it is
 * confined to the log: data files are never opened, only listed and handed to a recipient. Delta
 * Kernel does the log replay — checkpoints, tombstones and protocol versions are its business, not
 * ours — so this class only translates between Kernel's shapes and {@link DeltaSnapshot}.
 */
@Service
public class DeltaLogReader {

  private static final Logger log = LoggerFactory.getLogger(DeltaLogReader.class);

  /** Field names as the Delta log writes them, read out of the action structs by name. */
  private static final String STATS_FIELD = "stats";
  private static final String PATH_FIELD = "path";
  private static final String SIZE_FIELD = "size";
  private static final String PARTITION_VALUES_FIELD = "partitionValues";
  private static final String VERSION_FIELD = "version";
  private static final String TIMESTAMP_FIELD = "timestamp";

  /** The actions a change feed is made of. Metadata and protocol changes are read for context. */
  private static final Set<DeltaAction> CHANGE_ACTIONS =
      Set.of(DeltaAction.ADD, DeltaAction.REMOVE, DeltaAction.CDC);

  /**
   * @param includeFiles whether to list the version's data files, which is the expensive part
   * @throws ApiException if the log cannot be read, phrased for the recipient that asked
   */
  public DeltaSnapshot read(
      String tableRoot, StorageCredentials credentials, DeltaVersion at, boolean includeFiles) {
    Engine engine = engineFor(credentials);
    return reading(
        tableRoot,
        credentials,
        () -> {
          Snapshot snapshot = snapshot(engine, tableRoot, at);
          SnapshotImpl impl = (SnapshotImpl) snapshot;
          return new DeltaSnapshot(
              snapshot.getVersion(),
              snapshot.getTimestamp(engine),
              protocolOf(impl),
              metadataOf(impl),
              includeFiles ? files(engine, snapshot) : List.of());
        });
  }

  /**
   * What changed between two versions, inclusive, in commit order.
   *
   * <p>The protocol and metadata come from the ending version, so a reader learns the schema the
   * changes are shaped by. Kernel hands back the log's own actions here rather than a replayed
   * snapshot, which is exactly what a change feed is: history, not state.
   */
  public DeltaChanges changes(
      String tableRoot, StorageCredentials credentials, long startVersion, long endVersion) {
    if (endVersion < startVersion) {
      throw ApiException.invalidParameter(
          "the ending version " + endVersion + " is before the starting version " + startVersion);
    }
    Engine engine = engineFor(credentials);
    DeltaSnapshot ending = read(tableRoot, credentials, DeltaVersion.of(endVersion), false);
    return reading(
        tableRoot,
        credentials,
        () -> {
          String root = hadoopPath(tableRoot);
          List<DeltaChanges.Change> changes = new ArrayList<>();
          TableImpl table = (TableImpl) table(engine, tableRoot);
          try (CloseableIterator<ColumnarBatch> batches =
              table.getChanges(engine, startVersion, endVersion, CHANGE_ACTIONS)) {
            while (batches.hasNext()) {
              collectChanges(batches.next(), root, changes);
            }
          } catch (IOException e) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                ErrorCodes.INTERNAL_ERROR,
                "failed while reading the table's change files: " + e.getMessage());
          }
          return new DeltaChanges(ending, changes);
        });
  }

  /**
   * Each row of a change batch carries the commit's version and timestamp and exactly one action.
   * Anything that is not a data file — a metadata or protocol change mid-range — is skipped, since the
   * parquet response format has no line for it and the ending version's own metadata is already sent.
   */
  private static void collectChanges(
      ColumnarBatch batch, String tableRoot, List<DeltaChanges.Change> changes) throws IOException {
    try (CloseableIterator<Row> rows = batch.getRows()) {
      while (rows.hasNext()) {
        Row row = rows.next();
        long version = row.getLong(row.getSchema().indexOf(VERSION_FIELD));
        long timestamp = row.getLong(row.getSchema().indexOf(TIMESTAMP_FIELD));
        for (DeltaAction action : CHANGE_ACTIONS) {
          int ordinal = row.getSchema().indexOf(action.colName);
          if (ordinal < 0 || row.isNullAt(ordinal)) {
            continue;
          }
          changes.add(change(kindOf(action), row.getStruct(ordinal), tableRoot, version, timestamp));
        }
      }
    }
  }

  private static DeltaChanges.Change change(
      DeltaChanges.Kind kind, Row action, String tableRoot, long version, long timestamp) {
    return new DeltaChanges.Change(
        kind,
        absolute(tableRoot, string(action, PATH_FIELD)),
        longOrZero(action, SIZE_FIELD),
        version,
        timestamp,
        partitionValues(action),
        kind == DeltaChanges.Kind.ADD ? string(action, STATS_FIELD) : null);
  }

  private static DeltaChanges.Kind kindOf(DeltaAction action) {
    return switch (action) {
      case ADD -> DeltaChanges.Kind.ADD;
      case CDC -> DeltaChanges.Kind.CDF;
      default -> DeltaChanges.Kind.REMOVE;
    };
  }

  private static String absolute(String tableRoot, String path) {
    return path == null ? null : tableRoot + "/" + requireInsideTable(path);
  }

  /**
   * Refuses a path that leaves the table being shared, and decodes the rest.
   *
   * <p>A shared table is a directory, and the grant behind it says nothing about anywhere else, so a
   * log entry naming a file elsewhere — an absolute path, as a shallow clone writes, or one climbing
   * out with {@code ..} — must not be signed. Signing it would hand a recipient bytes the provider
   * never shared, and the server would be the one that fetched them.
   *
   * <p>What is left is relative and URI-escaped, since that is how the log writes a path holding a
   * space or a reserved character, and it has to be decoded before it can be signed.
   */
  static String requireInsideTable(String path) {
    String decoded = path;
    try {
      decoded = new URI(path).getPath();
    } catch (URISyntaxException e) {
      // A path the log wrote unescaped, which is still a usable path.
    }
    if (decoded.startsWith("/") || path.contains("://") || climbsOut(decoded)) {
      throw ApiException.notImplemented(
          "the log of this table records the file '"
              + path
              + "', which lies outside the table's own directory; a table sharing files from "
              + "elsewhere — a shallow clone, for instance — cannot be served in url access mode, "
              + "so use dir access mode and temporary-table-credentials to read it");
    }
    return decoded;
  }

  private static boolean climbsOut(String path) {
    for (String segment : path.split("/")) {
      if (segment.equals("..")) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, String> partitionValues(Row action) {
    int ordinal = action.getSchema().indexOf(PARTITION_VALUES_FIELD);
    if (ordinal < 0 || action.isNullAt(ordinal)) {
      return Map.of();
    }
    return VectorUtils.toJavaMap(action.getMap(ordinal));
  }

  private static String string(Row row, String field) {
    int ordinal = row.getSchema().indexOf(field);
    return ordinal < 0 || row.isNullAt(ordinal) ? null : row.getString(ordinal);
  }

  private static long longOrZero(Row row, String field) {
    int ordinal = row.getSchema().indexOf(field);
    return ordinal < 0 || row.isNullAt(ordinal) ? 0 : row.getLong(ordinal);
  }

  /**
   * The earliest version at or after a timestamp, which is what the protocol's
   * {@code startingTimestamp} asks for. Kernel answers the opposite question — the latest version at
   * or before — so this walks one commit forward unless that timestamp lands exactly on a commit.
   */
  public long earliestVersionAtOrAfter(
      String tableRoot, StorageCredentials credentials, Instant timestamp) {
    Engine engine = engineFor(credentials);
    return reading(
        tableRoot,
        credentials,
        () -> {
          Table table = table(engine, tableRoot);
          long latest = latestSnapshot(engine, table, tableRoot).getVersion();
          Snapshot atOrBefore;
          try {
            atOrBefore = table.getSnapshotAsOfTimestamp(engine, timestamp.toEpochMilli());
          } catch (KernelException e) {
            // No version at or before the timestamp, so the whole table is after it.
            return 0L;
          }
          if (atOrBefore.getTimestamp(engine) == timestamp.toEpochMilli()) {
            return atOrBefore.getVersion();
          }
          long next = atOrBefore.getVersion() + 1;
          if (next > latest) {
            throw ApiException.invalidParameter(
                "the table has no version at or after " + timestamp + "; the latest is " + latest);
          }
          return next;
        });
  }

  private Snapshot snapshot(Engine engine, String tableRoot, DeltaVersion at) {
    Table table = table(engine, tableRoot);
    if (at.isLatest()) {
      return latestSnapshot(engine, table, tableRoot);
    }
    try {
      return at.version() != null
          ? table.getSnapshotAsOfVersion(engine, at.version())
          : table.getSnapshotAsOfTimestamp(engine, at.timestamp().toEpochMilli());
    } catch (TableNotFoundException e) {
      throw noLog(tableRoot, e);
    } catch (KernelException e) {
      // Asking for a version or time the table never had is the client's mistake.
      throw ApiException.invalidParameter(e.getMessage());
    }
  }

  private Snapshot latestSnapshot(Engine engine, Table table, String tableRoot) {
    try {
      return table.getLatestSnapshot(engine);
    } catch (TableNotFoundException e) {
      throw noLog(tableRoot, e);
    }
  }

  private Table table(Engine engine, String tableRoot) {
    return Table.forPath(engine, hadoopPath(tableRoot));
  }

  private static DeltaSnapshot.Protocol protocolOf(SnapshotImpl snapshot) {
    io.delta.kernel.internal.actions.Protocol protocol = snapshot.getProtocol();
    return new DeltaSnapshot.Protocol(
        protocol.getMinReaderVersion(),
        protocol.getMinWriterVersion(),
        List.copyOf(protocol.getReaderFeatures()),
        List.copyOf(protocol.getWriterFeatures()));
  }

  private static DeltaSnapshot.Metadata metadataOf(SnapshotImpl snapshot) {
    io.delta.kernel.internal.actions.Metadata metadata = snapshot.getMetadata();
    Format format = metadata.getFormat();
    return new DeltaSnapshot.Metadata(
        metadata.getId(),
        metadata.getName().orElse(null),
        metadata.getDescription().orElse(null),
        format.getProvider(),
        format.getOptions(),
        metadata.getSchemaString(),
        partitionColumns(metadata),
        metadata.getConfiguration());
  }

  /**
   * The partition columns as the log lists them, which is the order a reader builds a partition path
   * in. Kernel's other accessor, {@code getPartitionColNames}, lower-cases the names to compare them
   * case-insensitively, so it cannot be used to answer what the columns are called.
   */
  private static List<String> partitionColumns(
      io.delta.kernel.internal.actions.Metadata metadata) {
    return VectorUtils.toJavaList(metadata.getPartitionColumns());
  }

  private List<DeltaSnapshot.File> files(Engine engine, Snapshot snapshot) {
    ScanBuilder builder = snapshot.getScanBuilder();
    Scan scan = builder.build();
    List<DeltaSnapshot.File> files = new ArrayList<>();
    try (CloseableIterator<FilteredColumnarBatch> batches = scanFiles(engine, scan)) {
      while (batches.hasNext()) {
        try (CloseableIterator<Row> rows = batches.next().getRows()) {
          while (rows.hasNext()) {
            files.add(file(rows.next()));
          }
        }
      }
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.INTERNAL_ERROR,
          "failed while listing the table's files: " + e.getMessage());
    }
    return files;
  }

  /** Statistics are optional in the scan, and the protocol wants them, so ask for them. */
  private static CloseableIterator<FilteredColumnarBatch> scanFiles(Engine engine, Scan scan) {
    return scan instanceof ScanImpl impl ? impl.getScanFiles(engine, true) : scan.getScanFiles(engine);
  }

  /**
   * Kernel has already resolved the path against the table root, so its answer is what gets signed;
   * the log's own entry is checked first, because only that says whether the file was the table's to
   * begin with.
   */
  private static DeltaSnapshot.File file(Row scanFileRow) {
    Row addFile = scanFileRow.getStruct(InternalScanFileUtils.ADD_FILE_ORDINAL);
    requireInsideTable(string(addFile, PATH_FIELD));
    FileStatus status = InternalScanFileUtils.getAddFileStatus(scanFileRow);
    return new DeltaSnapshot.File(
        status.getPath(),
        status.getSize(),
        InternalScanFileUtils.getPartitionValues(scanFileRow),
        stats(addFile));
  }

  private static String stats(Row addFile) {
    int ordinal = addFile.getSchema().indexOf(STATS_FIELD);
    if (ordinal < 0 || addFile.isNullAt(ordinal)) {
      return null;
    }
    return addFile.getString(ordinal);
  }

  private Engine engineFor(StorageCredentials credentials) {
    return DefaultEngine.create(hadoopConfiguration(credentials));
  }

  /**
   * Runs a read and turns a failure to reach storage into an answer the recipient can act on. The
   * common one by far is a filesystem this build has no driver for, which is a deployment fact rather
   * than anything about the request, so it is reported as unimplemented with the jar that fixes it.
   */
  private <T> T reading(String tableRoot, StorageCredentials credentials, Supplier<T> read) {
    try {
      return read.get();
    } catch (KernelEngineException e) {
      if (missingFileSystemDriver(e)) {
        throw missingFileSystem(credentials, tableRoot);
      }
      log.warn("Could not read the Delta log under {}", tableRoot, e);
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          ErrorCodes.INTERNAL_ERROR,
          "the table's storage could not be reached to read its Delta log");
    }
  }

  private static boolean missingFileSystemDriver(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError) {
        return true;
      }
    }
    return false;
  }

  /**
   * Hands the catalog's credentials to Hadoop, which is how Kernel's default engine reaches storage.
   * The keys differ per provider but the values are the same ones a recipient gets from
   * {@code temporary-table-credentials}: this server reads the log with exactly the access it hands
   * out, never more.
   */
  private Configuration hadoopConfiguration(StorageCredentials credentials) {
    Configuration conf = new Configuration();
    if (credentials == null) {
      return conf;
    }
    switch (credentials.provider()) {
      case AWS, R2 -> {
        conf.set(
            "fs.s3a.aws.credentials.provider",
            "org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider");
        conf.set("fs.s3a.access.key", credentials.require(StorageCredentialKeys.ACCESS_KEY_ID));
        conf.set("fs.s3a.secret.key", credentials.require(StorageCredentialKeys.SECRET_ACCESS_KEY));
        conf.set("fs.s3a.session.token", credentials.require(StorageCredentialKeys.SESSION_TOKEN));
      }
      case AZURE -> {
        conf.set("fs.azure.account.auth.type", "SAS");
        conf.set(
            "fs.azure.sas.token.provider.type",
            "org.apache.hadoop.fs.azurebfs.sas.FixedSASTokenProvider");
        conf.set("fs.azure.sas.fixed.token", credentials.require(StorageCredentialKeys.SAS_TOKEN));
      }
      case GCP ->
          throw ApiException.notImplemented(
              "reading a Delta log on Google Cloud Storage needs the GCS connector, which this "
                  + "build does not ship; use dir access mode and temporary-table-credentials");
    }
    return conf;
  }

  /**
   * Hadoop addresses S3 as {@code s3a}, while catalogs report {@code s3}. Everything else is passed
   * through, including bare paths, which the local filesystem handles.
   */
  static String hadoopPath(String location) {
    if (location == null || location.isBlank()) {
      throw ApiException.notFound("the catalog reports no storage location for this table");
    }
    String trimmed = location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
    return trimmed.toLowerCase(Locale.ROOT).startsWith("s3://")
        ? "s3a://" + trimmed.substring("s3://".length())
        : trimmed;
  }

  private ApiException noLog(String tableRoot, Exception cause) {
    log.warn("No Delta log under {}: {}", tableRoot, cause.getMessage());
    return ApiException.notFound(
        "no Delta log was found under '" + tableRoot + "', so the table cannot be read");
  }

  private ApiException missingFileSystem(StorageCredentials credentials, String tableRoot) {
    String jar =
        credentials != null && credentials.provider() == CloudProvider.AZURE
            ? "hadoop-azure"
            : "hadoop-aws";
    return ApiException.notImplemented(
        "reading the Delta log under '"
            + tableRoot
            + "' needs "
            + jar
            + " on the classpath, which this build does not ship; use dir access mode and "
            + "temporary-table-credentials to read the table");
  }
}
