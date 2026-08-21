package io.opensharing.asset.delta;

import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.TableFormat;
import io.opensharing.config.OpenSharingProperties;
import io.opensharing.http.ApiException;
import io.opensharing.protocol.QueryTableRequest;
import io.opensharing.protocol.TableAction;
import io.opensharing.serving.ActionStream;
import io.opensharing.serving.ProtocolHeaders;
import io.opensharing.serving.TableOperations;
import io.opensharing.serving.TableRequests;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The table read operations for a Delta table, served in url access mode.
 *
 * <p>The server reads the table's log with credentials the catalog minted for it, and hands back
 * per-file urls signed from those same credentials, so a recipient reads the bytes directly without
 * ever holding a credential. A recipient whose engine can replay a Delta log itself is better served by
 * dir access mode, which skips all of this — and is all that is left when {@code
 * opensharing.delta.url-access-enabled} is off.
 *
 * <p>This is where the protocol's Delta specifics live: what a version selector means, what a
 * capabilities header may ask for, and which file id schemes exist.
 */
@Component
public class DeltaTableOperations implements TableOperations {

  private static final Set<String> FILE_ID_SCHEMES = Set.of("parquet");

  private final DeltaTableService tables;
  private final DeltaResponseMapper mapper;
  private final boolean urlAccessEnabled;

  public DeltaTableOperations(
      DeltaTableService tables, DeltaResponseMapper mapper, OpenSharingProperties properties) {
    this.tables = tables;
    this.mapper = mapper;
    this.urlAccessEnabled = properties.getDelta().isUrlAccessEnabled();
  }

  @Override
  public TableFormat format() {
    return TableFormat.DELTA;
  }

  @Override
  public long version(SharedDataObjectEntity table, TableRequests.Version request) {
    requireUrlAccess();
    return tables.version(table, DeltaVersion.parse(request.startingTimestamp()));
  }

  @Override
  public ActionStream metadata(SharedDataObjectEntity table, TableRequests.Metadata request) {
    requireUrlAccess();
    DeltaSharingCapabilities capabilities = accepted(request.capabilities());
    DeltaVersion at = DeltaVersion.from(request.version(), request.timestamp());
    DeltaTable delta = tables.read(table, at, false);
    return stream(mapper.metadata(table, delta, !at.isLatest()), delta, capabilities, null);
  }

  @Override
  public ActionStream query(SharedDataObjectEntity table, TableRequests.Query request) {
    requireUrlAccess();
    QueryTableRequest query = request.body();
    DeltaSharingCapabilities capabilities = accepted(request.capabilities());
    String fileIdScheme = fileIdScheme(request.fileIdHash());
    if (query.startingVersion() != null) {
      throw ApiException.notImplemented(
          "querying data change files by startingVersion is not implemented; omit it to read a "
              + "snapshot of the table");
    }

    DeltaVersion at = DeltaVersion.from(query.version(), query.timestamp());
    DeltaTable delta = tables.read(table, at, true);
    return stream(
        mapper.query(table, delta, !at.isLatest(), capabilities),
        delta,
        capabilities,
        fileIdScheme);
  }

  /**
   * Only a table with the change data feed turned on records enough to answer fully, which is the log's
   * own business — an untracked window simply yields the added and removed files it can see.
   */
  @Override
  public ActionStream changes(SharedDataObjectEntity table, TableRequests.Changes request) {
    requireUrlAccess();
    DeltaSharingCapabilities capabilities = accepted(request.capabilities());
    if (request.includeHistoricalMetadata()) {
      throw ApiException.notImplemented(
          "includeHistoricalMetadata is not implemented; the response carries the metadata of the "
              + "window's ending version, so a schema change inside the window is not reported");
    }
    String fileIdScheme = fileIdScheme(request.fileIdHash());

    DeltaTableService.ChangeFeed feed =
        tables.changes(
            table,
            request.startingVersion(),
            DeltaVersion.parse(request.startingTimestamp()),
            request.endingVersion(),
            DeltaVersion.parse(request.endingTimestamp()));
    return stream(
        mapper.changes(table, feed.table(), feed.changes(), capabilities),
        feed.table(),
        capabilities,
        fileIdScheme);
  }

  private ActionStream stream(
      List<TableAction> actions,
      DeltaTable delta,
      DeltaSharingCapabilities capabilities,
      String fileIdScheme) {
    return ActionStream.of(actions)
        .header(ProtocolHeaders.TABLE_VERSION, Long.toString(delta.snapshot().version()))
        .header(ProtocolHeaders.CAPABILITIES, capabilities.responseHeaderValue())
        .header(ProtocolHeaders.FILE_ID_HASH, fileIdScheme)
        .build();
  }

  private static DeltaSharingCapabilities accepted(String header) {
    DeltaSharingCapabilities capabilities = DeltaSharingCapabilities.parse(header);
    capabilities.requireParquetIsAcceptable();
    return capabilities;
  }

  /**
   * The scheme a client wants file ids derived by. Only the parquet-aligned one exists here, and the
   * protocol asks for a 400 on anything else and for the accepted value to be echoed back.
   */
  private static String fileIdScheme(String requested) {
    if (requested == null || requested.isBlank()) {
      return null;
    }
    String scheme = requested.trim().toLowerCase(Locale.ROOT);
    if (!FILE_ID_SCHEMES.contains(scheme)) {
      throw ApiException.invalidParameter(
          "unsupported fileidhash '" + requested + "'; this server derives ids the parquet way");
    }
    return scheme;
  }

  private void requireUrlAccess() {
    if (!urlAccessEnabled) {
      throw ApiException.notImplemented(
          "url access mode is turned off on this server; call temporary-table-credentials and read "
              + "the table's storage location directly");
    }
  }
}
