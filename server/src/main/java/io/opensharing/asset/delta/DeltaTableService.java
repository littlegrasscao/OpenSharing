package io.opensharing.asset.delta;

import io.opensharing.asset.AssetResolutionService;
import io.opensharing.asset.CredentialVendingService;
import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.ResolvedAsset;
import io.opensharing.catalog.StorageCredentials;
import io.opensharing.catalog.TableFormat;
import io.opensharing.http.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Serves the Delta read operations from a shared object: re-resolves it in the catalog, gets
 * credentials for wherever it now lives, and reads its log.
 *
 * <p>Every call re-resolves rather than trusting the stored snapshot, for the same reason credential
 * vending does: a table that moved or that the server may no longer read must not be served from
 * stale state.
 */
@Service
public class DeltaTableService {

  private final AssetResolutionService resolution;
  private final CredentialVendingService credentials;
  private final DeltaLogReader reader;

  public DeltaTableService(
      AssetResolutionService resolution,
      CredentialVendingService credentials,
      DeltaLogReader reader) {
    this.resolution = resolution;
    this.credentials = credentials;
    this.reader = reader;
  }

  public DeltaTable read(SharedDataObjectEntity object, DeltaVersion at, boolean includeFiles) {
    ResolvedAsset resolved = requireDelta(object);
    StorageCredentials minted = credentials.mint(resolved, resolved.storageLocation());
    return new DeltaTable(
        resolved, minted, reader.read(resolved.storageLocation(), minted, at, includeFiles));
  }

  /**
   * The change feed over a window, with the table it belongs to.
   *
   * <p>A window can be named by version or by time at either end. A missing start means from the
   * table's beginning, a missing end means up to now, and a timestamp is resolved the way the
   * protocol asks: the start moves forward to the first version at or after it, the end back to the
   * last version at or before it.
   *
   * @param includeHistory whether the schema and protocol changes inside the window are wanted,
   *     which also decides where the response's own metadata comes from
   */
  public ChangeFeed changes(
      SharedDataObjectEntity object,
      Long startingVersion,
      Instant startingTimestamp,
      Long endingVersion,
      Instant endingTimestamp,
      boolean includeHistory) {
    if (startingVersion != null && startingTimestamp != null) {
      throw ApiException.invalidParameter(
          "startingVersion and startingTimestamp are mutually exclusive");
    }
    if (endingVersion != null && endingTimestamp != null) {
      throw ApiException.invalidParameter(
          "endingVersion and endingTimestamp are mutually exclusive");
    }
    ResolvedAsset resolved = requireDelta(object);
    StorageCredentials minted = credentials.mint(resolved, resolved.storageLocation());
    String location = resolved.storageLocation();

    long start =
        startingTimestamp != null
            ? reader.earliestVersionAtOrAfter(location, minted, startingTimestamp)
            : (startingVersion == null ? 0 : startingVersion);
    long end =
        endingVersion != null
            ? endingVersion
            : reader.read(location, minted, versionAt(endingTimestamp), false).version();
    return window(resolved, minted, start, end, true, includeHistory, includeHistory ? start : end);
  }

  /**
   * The data change files from a version onwards, which is what a stream following the table asks
   * for rather than a snapshot of it.
   *
   * <p>Only the files each commit added and removed are reported: the recorded row-level changes a
   * change data feed carries belong to the changes endpoint, and a stream rebuilding the table from
   * commits would double-count them.
   *
   * <p>The response is headed by the starting version's own protocol and metadata, since that is
   * where the reader is starting and the shape the first files it gets were written under.
   */
  public ChangeFeed changesFrom(
      SharedDataObjectEntity object,
      long startingVersion,
      Long endingVersion,
      boolean includeHistory) {
    ResolvedAsset resolved = requireDelta(object);
    StorageCredentials minted = credentials.mint(resolved, resolved.storageLocation());
    String location = resolved.storageLocation();
    long end =
        endingVersion != null
            ? endingVersion
            : reader.read(location, minted, DeltaVersion.latest(), false).version();
    return window(resolved, minted, startingVersion, end, false, includeHistory, startingVersion);
  }

  private ChangeFeed window(
      ResolvedAsset resolved,
      StorageCredentials minted,
      long start,
      long end,
      boolean includeCdc,
      boolean includeHistory,
      long headVersion) {
    String location = resolved.storageLocation();
    List<DeltaChanges.Entry> entries =
        reader.changes(location, minted, start, end, includeCdc, includeHistory);
    DeltaSnapshot head = reader.read(location, minted, DeltaVersion.of(headVersion), false);
    return new ChangeFeed(
        new DeltaTable(resolved, minted, head),
        withoutWhatTheHeadAlreadySays(entries, head.version()),
        start);
  }

  /**
   * A response opens with one version's protocol and metadata, so the log's own actions for that
   * version would only say it twice. The files of that version stay, since nothing else has named
   * them.
   */
  private static List<DeltaChanges.Entry> withoutWhatTheHeadAlreadySays(
      List<DeltaChanges.Entry> entries, long headVersion) {
    return entries.stream()
        .filter(
            entry ->
                entry instanceof DeltaChanges.FileChange || entry.version() != headVersion)
        .toList();
  }

  private static DeltaVersion versionAt(Instant timestamp) {
    return timestamp == null ? DeltaVersion.latest() : DeltaVersion.asOf(timestamp);
  }

  /**
   * A window of changes and the table it came from, since signing its urls needs both.
   *
   * @param startVersion the first version the window covers, which is what the protocol stamps on
   *     the response, whatever version the metadata came from
   */
  public record ChangeFeed(
      DeltaTable table, List<DeltaChanges.Entry> changes, long startVersion) {

    public ChangeFeed {
      changes = changes == null ? List.of() : List.copyOf(changes);
    }
  }

  /**
   * The version alone, which is the cheap question the protocol has a whole endpoint for.
   *
   * @param startingTimestamp when set, the earliest version at or after it, rather than the latest
   */
  public long version(SharedDataObjectEntity object, Instant startingTimestamp) {
    ResolvedAsset resolved = requireDelta(object);
    StorageCredentials minted = credentials.mint(resolved, resolved.storageLocation());
    if (startingTimestamp != null) {
      return reader.earliestVersionAtOrAfter(
          resolved.storageLocation(), minted, startingTimestamp);
    }
    return reader.read(resolved.storageLocation(), minted, DeltaVersion.latest(), false).version();
  }

  /**
   * A second look at the format, after the endpoint has already routed here by the one recorded on the
   * shared object. The two disagree only when the catalog has rewritten the table in another format
   * since it was last resolved, which is rare but must not end in a Delta log being read from
   * something that is no longer one.
   */
  private ResolvedAsset requireDelta(SharedDataObjectEntity object) {
    ResolvedAsset resolved = resolution.resolveForServing(object);
    if (resolved.format() != TableFormat.DELTA) {
      throw ApiException.notImplemented(
          "'"
              + object.getSharedAsName()
              + "' is "
              + (resolved.format() == null
                  ? "of no format the catalog states"
                  : resolved.format().wireName())
              + " in the catalog now, so it can no longer be read as Delta; call "
              + "temporary-table-credentials and read its storage location directly");
    }
    return resolved;
  }
}
