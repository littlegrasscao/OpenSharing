package io.opensharing.serving;

import io.opensharing.http.ApiException;
import io.opensharing.http.ProtocolMediaType;
import io.opensharing.protocol.IcebergConfig;
import io.opensharing.recipient.RecipientPrincipal;
import io.opensharing.share.ShareAccessService;
import io.opensharing.share.ShareEntity;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Iceberg REST catalog surface of {@code spec/protocols/TABLES.md}, mounted where the profile
 * file's {@code icebergEndpoint} points.
 *
 * <p>Only the {@code /v1/config} handshake is served, so a client can discover the server and be told
 * which prefix to address a share by. The five catalog operations answer with a protocol error instead
 * of a bare 404, the same stance {@code IcebergTableOperations} takes on the table read operations.
 *
 * <p>Iceberg tables are still readable today through {@code dir} access mode: call
 * {@code temporary-table-credentials} and read the table's storage location directly.
 */
@RestController
@RequestMapping(value = RecipientApi.ICEBERG, produces = ProtocolMediaType.JSON_UTF8)
public class IcebergCatalogController {

  private static final List<String> ENDPOINTS =
      List.of(
          "GET /v1/{prefix}/namespaces",
          "GET /v1/{prefix}/namespaces/{namespace}",
          "GET /v1/{prefix}/namespaces/{namespace}/tables",
          "GET /v1/{prefix}/namespaces/{namespace}/tables/{table}",
          "POST /v1/{prefix}/namespaces/{namespace}/tables/{table}/metrics");

  private final ShareAccessService access;

  public IcebergCatalogController(ShareAccessService access) {
    this.access = access;
  }

  /**
   * @param warehouse the share to read, echoed back as the {@code shares/{share}} path prefix under
   *     which the client addresses every later call
   */
  @GetMapping("/config")
  public IcebergConfig config(RecipientPrincipal principal, @RequestParam String warehouse) {
    ShareEntity share = access.requireShare(principal, warehouse);
    return new IcebergConfig(Map.of(), Map.of("prefix", "shares/" + share.getName()), ENDPOINTS);
  }

  @GetMapping("/shares/{share}/namespaces")
  public void listNamespaces(RecipientPrincipal principal, @PathVariable String share) {
    rejectUnserved(principal, share, "listing Iceberg namespaces");
  }

  @GetMapping("/shares/{share}/namespaces/{namespace}")
  public void loadNamespaceMetadata(
      RecipientPrincipal principal, @PathVariable String share, @PathVariable String namespace) {
    rejectUnserved(principal, share, "loading Iceberg namespace metadata");
  }

  @GetMapping("/shares/{share}/namespaces/{namespace}/tables")
  public void listTables(
      RecipientPrincipal principal, @PathVariable String share, @PathVariable String namespace) {
    rejectUnserved(principal, share, "listing Iceberg tables");
  }

  @GetMapping("/shares/{share}/namespaces/{namespace}/tables/{table}")
  public void loadTable(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String namespace,
      @PathVariable String table) {
    rejectUnserved(principal, share, "loading an Iceberg table");
  }

  @PostMapping("/shares/{share}/namespaces/{namespace}/tables/{table}/metrics")
  public void reportMetrics(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String namespace,
      @PathVariable String table) {
    rejectUnserved(principal, share, "reporting Iceberg scan metrics");
  }

  /** Authorizes the share first, so an ungranted share reads as missing rather than unbuilt. */
  private void rejectUnserved(RecipientPrincipal principal, String share, String operation) {
    access.requireShare(principal, share);
    throw ApiException.notImplemented(
        operation
            + " requires the Iceberg REST catalog, which this build does not serve yet; use "
            + "temporary-table-credentials and read the table's storage location directly");
  }
}
