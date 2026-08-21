package io.opensharing.serving;

import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.TableFormat;

/**
 * The protocol's table read operations, as one table format answers them.
 *
 * <p>These four endpoints belong to a table, not to a format: {@code spec/protocols/TABLES.md} defines
 * them for anything a share can hold, and a recipient asking for a table's metadata should not have to
 * know what the bytes underneath are. So the endpoint resolves the table and asks whichever
 * implementation claims its format, and a format nobody implements yet is a server-side gap — answered
 * as such, rather than as the recipient having asked something invalid.
 *
 * <p>An implementation is trusted to re-check the format of what it is handed. Dispatch reads the
 * format from the stored snapshot, which is cheap and nearly always right, but a table the catalog has
 * since rewritten in another format would arrive at the wrong implementation and must be refused rather
 * than misread.
 */
public interface TableOperations {

  /** The format this serves. One implementation each, resolved by {@link TableFormatRegistry}. */
  TableFormat format();

  long version(SharedDataObjectEntity table, TableRequests.Version request);

  ActionStream metadata(SharedDataObjectEntity table, TableRequests.Metadata request);

  ActionStream query(SharedDataObjectEntity table, TableRequests.Query request);

  ActionStream changes(SharedDataObjectEntity table, TableRequests.Changes request);
}
