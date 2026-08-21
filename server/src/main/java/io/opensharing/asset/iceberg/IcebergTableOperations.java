package io.opensharing.asset.iceberg;

import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.catalog.TableFormat;
import io.opensharing.http.ApiException;
import io.opensharing.serving.ActionStream;
import io.opensharing.serving.TableOperations;
import io.opensharing.serving.TableRequests;
import org.springframework.stereotype.Component;

/**
 * An Iceberg table's read operations, which this build does not serve.
 *
 * <p>It is registered rather than absent so that the refusal can say something true: the endpoint
 * exists for this table and the recipient asked correctly, but an Iceberg table is read through the
 * Iceberg REST catalog, which is not built yet. Until it is, dir access mode serves these tables in
 * full — {@code temporary-table-credentials} and the table's storage location are enough for an engine
 * that can read Iceberg itself.
 *
 * <p>The four operations are also not simply Delta's with another name: a snapshot is named by an
 * Iceberg snapshot id, and files come from manifests rather than a log, so this is a place to fill in
 * rather than a case to fold into the Delta implementation.
 */
@Component
public class IcebergTableOperations implements TableOperations {

  @Override
  public TableFormat format() {
    return TableFormat.ICEBERG;
  }

  @Override
  public long version(SharedDataObjectEntity table, TableRequests.Version request) {
    throw unserved(table, "a version");
  }

  @Override
  public ActionStream metadata(SharedDataObjectEntity table, TableRequests.Metadata request) {
    throw unserved(table, "metadata");
  }

  @Override
  public ActionStream query(SharedDataObjectEntity table, TableRequests.Query request) {
    throw unserved(table, "files");
  }

  @Override
  public ActionStream changes(SharedDataObjectEntity table, TableRequests.Changes request) {
    throw unserved(table, "a change feed");
  }

  private static ApiException unserved(SharedDataObjectEntity table, String what) {
    return ApiException.notImplemented(
        "serving "
            + what
            + " for the Iceberg table '"
            + table.getSharedAsName()
            + "' requires the Iceberg REST catalog, which this build does not serve yet; call "
            + "temporary-table-credentials and read the table's storage location directly");
  }
}
