package io.opensharing.serving;

import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.TableFormat;
import io.opensharing.http.ApiException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Picks the implementation that serves a table's format.
 *
 * <p>Implementations are collected rather than named, so a new format is a class and nothing else —
 * the same arrangement {@code UrlSigners} uses for storage schemes.
 */
@Component
public class TableFormatRegistry {

  private final Map<TableFormat, TableOperations> byFormat = new EnumMap<>(TableFormat.class);

  public TableFormatRegistry(List<TableOperations> implementations) {
    for (TableOperations operations : implementations) {
      TableOperations existing = byFormat.put(operations.format(), operations);
      if (existing != null) {
        throw new IllegalStateException(
            "two implementations claim the " + operations.format() + " table format");
      }
    }
  }

  /**
   * @throws ApiException as not implemented when nothing serves the table's format, including when the
   *     catalog states no format at all — in either case the recipient can still read the bytes through
   *     dir access mode, which is what the message says
   */
  public TableOperations forTable(SharedDataObjectEntity table) {
    TableOperations operations = byFormat.get(table.getSourceFormat());
    if (operations == null) {
      throw ApiException.notImplemented(
          "'"
              + table.getSharedAsName()
              + "' is "
              + describe(table.getSourceFormat())
              + ", which this server does not serve through the table read operations; call "
              + "temporary-table-credentials and read its storage location directly");
    }
    return operations;
  }

  private static String describe(TableFormat format) {
    return format == null ? "of no format the catalog states" : "a " + format.wireName() + " table";
  }
}
