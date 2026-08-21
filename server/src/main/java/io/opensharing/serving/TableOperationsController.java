package io.opensharing.serving;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.asset.SharedDataObjectEntity;
import io.opensharing.asset.SharedTableService;
import io.opensharing.protocol.QueryTableRequest;
import io.opensharing.protocol.TableAction;
import io.opensharing.recipient.RecipientPrincipal;
import io.opensharing.share.ShareAccessService;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read operations the protocol defines for a table: what version it is at, what shape it has, what
 * files make it up, and what changed over a window.
 *
 * <p>Nothing here knows a table format. The endpoint authorizes the share, resolves the table —
 * whether shared in its own right or through the schema holding it — and hands the request to whichever
 * {@link TableOperations} serves that format, then writes out the actions and headers it gets back.
 * Which version a timestamp names, what a capabilities header may ask for, and how files are signed are
 * all decided on the other side of that seam.
 *
 * <p>Responses are newline-delimited JSON, one action per line.
 */
@RestController
@RequestMapping(RecipientApi.TABLE)
public class TableOperationsController {

  /** Spelled the way a parsed media type renders, so the published API lists it once. */
  private static final String NDJSON = "application/x-ndjson;charset=utf-8";

  private final ShareAccessService access;
  private final SharedTableService tables;
  private final TableFormatRegistry formats;
  private final ObjectMapper json;

  public TableOperationsController(
      ShareAccessService access,
      SharedTableService tables,
      TableFormatRegistry formats,
      ObjectMapper json) {
    this.access = access;
    this.tables = tables;
    this.formats = formats;
    this.json = json;
  }

  /**
   * The cheap question: what version is this table at? Answered in a header with no body, which is
   * what lets a client check a cache without paying for metadata.
   */
  @GetMapping("/version")
  public ResponseEntity<Void> version(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String schema,
      @PathVariable String table,
      @RequestParam(required = false) String startingTimestamp) {
    SharedDataObjectEntity object = require(principal, share, schema, table);
    long version =
        formats.forTable(object).version(object, new TableRequests.Version(startingTimestamp));
    return ResponseEntity.ok()
        .header(ProtocolHeaders.TABLE_VERSION, Long.toString(version))
        .build();
  }

  @GetMapping(value = "/metadata", produces = NDJSON)
  public ResponseEntity<String> metadata(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String schema,
      @PathVariable String table,
      @RequestParam(required = false) Long version,
      @RequestParam(required = false) String timestamp,
      @RequestHeader(name = ProtocolHeaders.CAPABILITIES, required = false) String capabilities) {
    SharedDataObjectEntity object = require(principal, share, schema, table);
    return ndjson(
        formats
            .forTable(object)
            .metadata(object, new TableRequests.Metadata(version, timestamp, capabilities)));
  }

  @PostMapping(value = "/query", produces = NDJSON)
  public ResponseEntity<String> query(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String schema,
      @PathVariable String table,
      @RequestBody(required = false) QueryTableRequest request,
      @RequestHeader(name = ProtocolHeaders.CAPABILITIES, required = false) String capabilities,
      @RequestHeader(name = ProtocolHeaders.FILE_ID_HASH, required = false) String fileIdHash) {
    SharedDataObjectEntity object = require(principal, share, schema, table);
    QueryTableRequest body = request == null ? QueryTableRequest.snapshot() : request;
    return ndjson(
        formats
            .forTable(object)
            .query(object, new TableRequests.Query(body, capabilities, fileIdHash)));
  }

  /**
   * The change data feed: what happened to the table over a window of versions, rather than what it
   * holds now.
   */
  @GetMapping(value = "/changes", produces = NDJSON)
  public ResponseEntity<String> changes(
      RecipientPrincipal principal,
      @PathVariable String share,
      @PathVariable String schema,
      @PathVariable String table,
      @RequestParam(required = false) Long startingVersion,
      @RequestParam(required = false) String startingTimestamp,
      @RequestParam(required = false) Long endingVersion,
      @RequestParam(required = false) String endingTimestamp,
      @RequestParam(required = false, defaultValue = "false") boolean includeHistoricalMetadata,
      @RequestHeader(name = ProtocolHeaders.CAPABILITIES, required = false) String capabilities,
      @RequestHeader(name = ProtocolHeaders.FILE_ID_HASH, required = false) String fileIdHash) {
    SharedDataObjectEntity object = require(principal, share, schema, table);
    return ndjson(
        formats
            .forTable(object)
            .changes(
                object,
                new TableRequests.Changes(
                    startingVersion,
                    startingTimestamp,
                    endingVersion,
                    endingTimestamp,
                    includeHistoricalMetadata,
                    capabilities,
                    fileIdHash)));
  }

  private ResponseEntity<String> ndjson(ActionStream stream) {
    ResponseEntity.BodyBuilder response = ResponseEntity.ok();
    stream.headers().forEach(response::header);
    return response.body(
        stream.actions().stream().map(this::line).collect(Collectors.joining("\n", "", "\n")));
  }

  private String line(TableAction action) {
    try {
      return json.writeValueAsString(action);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize a table action", e);
    }
  }

  /** A recipient reaches a table only through a share that has been granted to it. */
  private SharedDataObjectEntity require(
      RecipientPrincipal principal, String share, String schema, String table) {
    return tables.require(access.requireShare(principal, share), schema, table);
  }
}
