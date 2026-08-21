package io.opensharing;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Serves the Delta read operations end to end against a real Delta log on disk, over the same HTTP
 * surface a recipient uses. The catalog is pointed at the two-commit table under
 * {@code src/test/resources/delta-table}, so these tests exercise log replay, response shaping and
 * url signing together.
 */
class DeltaUrlAccessApiTest extends ServerTestBase {

  private static final String CAPABILITIES = "delta-sharing-capabilities";
  private static final String TABLE_VERSION = "Delta-Table-Version";

  private String share;
  private String token;

  @DynamicPropertySource
  static void pointTheCatalogAtALocalDeltaTable(DynamicPropertyRegistry registry)
      throws IOException {
    Path table = new ClassPathResource("delta-table/orders").getFile().toPath();
    Path catalog = Files.createTempFile("delta-catalog", ".yml");
    Files.writeString(
        catalog,
        """
        credentials:
          provider: AWS
          mode: FAKE
          ttlSeconds: 900
        assets:
          - identifier: main.sales.orders
            type: TABLE
            storageLocation: %s
            format: delta
          - identifier: main.sales.forecast
            type: TABLE
            storageLocation: /tmp/forecast
            format: iceberg
          - identifier: main.sales.notes
            type: TABLE
            storageLocation: /tmp/notes
            format: parquet
        """
            .formatted(table));
    registry.add("opensharing.catalog.local.file", () -> "file:" + catalog);
  }

  @BeforeEach
  void shareTheTable() throws Exception {
    share = createShare(unique("delta_share"));
    addTable(share, "sales.orders", "main.sales.orders");
    String recipient = unique("delta_recipient");
    token = createRecipientWithToken(recipient);
    grant(share, recipient);
  }

  @Test
  void offersUrlAccessOnADeltaTable() throws Exception {
    JsonNode tables = protocolGet(token, "/shares/" + share + "/all-tables");
    JsonNode orders = tables.get("items").get(0);

    List<String> modes = new ArrayList<>();
    orders.get("accessModes").forEach(mode -> modes.add(mode.asText()));
    assertEquals(List.of("dir", "url"), modes, "a Delta table this server can read offers both");
  }

  @Test
  void answersTheTableVersionInAHeaderWithNoBody() throws Exception {
    MvcResult result =
        perform(get(protocol("/version"))).andReturn();

    assertEquals(200, result.getResponse().getStatus());
    assertEquals("2", result.getResponse().getHeader(TABLE_VERSION));
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  void answersMetadataAsProtocolThenMetaData() throws Exception {
    MvcResult result = perform(get(protocol("/metadata"))).andReturn();
    List<JsonNode> lines = ndjson(result);

    assertEquals(2, lines.size());
    assertEquals(1, lines.get(0).get("protocol").get("minReaderVersion").asInt());
    JsonNode metadata = lines.get(1).get("metaData");
    assertEquals("11111111-2222-3333-4444-555555555555", metadata.get("id").asText());
    assertEquals("parquet", metadata.get("format").get("provider").asText());
    assertEquals("country", metadata.get("partitionColumns").get(0).asText());
    assertTrue(metadata.get("schemaString").asText().contains("order_id"));
    assertEquals("2", result.getResponse().getHeader(TABLE_VERSION));
    assertEquals("responseformat=parquet", result.getResponse().getHeader(CAPABILITIES));
  }

  @Test
  void namesTheTableLocationSoADirectoryClientCanUseIt() throws Exception {
    JsonNode metadata = ndjson(perform(get(protocol("/metadata"))).andReturn()).get(1).get("metaData");

    assertTrue(metadata.get("location").asText().endsWith("delta-table/orders"));
    assertEquals("dir", metadata.get("accessModes").get(0).asText());
  }

  @Test
  void answersQueryWithOneFilePerActiveAddAction() throws Exception {
    List<JsonNode> lines = ndjson(perform(post(protocol("/query")).content("{}")).andReturn());

    assertEquals(4, lines.size(), "protocol, metaData and one line per file");
    JsonNode metadata = lines.get(1).get("metaData");
    assertEquals(2, metadata.get("numFiles").asLong());
    assertEquals(2345 + 1300, metadata.get("size").asLong());

    JsonNode file = lines.get(2).get("file");
    assertNotNull(file.get("url").asText());
    assertNotNull(file.get("id").asText());
    assertTrue(file.get("size").asLong() > 0);
    assertTrue(file.get("stats").asText().contains("numRecords"));
    assertTrue(
        file.get("expirationTimestamp").asLong() > Instant.now().toEpochMilli(),
        "a url that has already expired is no use to a recipient");
  }

  @Test
  void keepsAFileIdStableAcrossRequests() throws Exception {
    String first = fileIdsOf(ndjson(perform(post(protocol("/query")).content("{}")).andReturn()));
    String second = fileIdsOf(ndjson(perform(post(protocol("/query")).content("{}")).andReturn()));

    assertEquals(first, second, "a client caches bytes against the id, so it must not move");
  }

  @Test
  void readsAnEarlierVersionAndStampsItOnTheFiles() throws Exception {
    List<JsonNode> lines =
        ndjson(perform(post(protocol("/query")).content("{\"version\":0}")).andReturn());

    assertEquals(3, lines.size(), "version 0 has one file");
    assertEquals(0, lines.get(1).get("metaData").get("version").asLong());
    assertEquals(0, lines.get(2).get("file").get("version").asLong());
    assertEquals(
        new ClassPathResource("delta-table/orders/_delta_log/00000000000000000000.json")
            .getFile()
            .lastModified(),
        lines.get(2).get("file").get("timestamp").asLong(),
        "the timestamp of the version the file belongs to, not of the file");
  }

  @Test
  void closesTheStreamWhenTheClientAsksItTo() throws Exception {
    List<JsonNode> lines =
        ndjson(
            perform(
                    post(protocol("/query"))
                        .content("{}")
                        .header(CAPABILITIES, "includeEndStreamAction=true"))
                .andReturn());

    JsonNode last = lines.get(lines.size() - 1).get("endStreamAction");
    assertNotNull(last, "the last line closes the stream");
    assertTrue(last.get("minUrlExpirationTimestamp").asLong() > Instant.now().toEpochMilli());
  }

  @Test
  void refusesARequestThatWillOnlyAcceptDeltaFormat() throws Exception {
    perform(get(protocol("/metadata")).header(CAPABILITIES, "responseFormat=delta"))
        .andExpect(status().isNotImplemented());
  }

  @Test
  void acceptsAClientThatCanTakeEitherFormat() throws Exception {
    MvcResult result =
        perform(get(protocol("/metadata")).header(CAPABILITIES, "responseFormat=delta,parquet"))
            .andReturn();

    assertEquals(200, result.getResponse().getStatus());
    assertEquals("responseformat=parquet", result.getResponse().getHeader(CAPABILITIES));
  }

  @Test
  void echoesTheFileIdSchemeItUsedAndRejectsOthers() throws Exception {
    MvcResult accepted =
        perform(post(protocol("/query")).content("{}").header("fileidhash", "PARQUET")).andReturn();
    assertEquals("parquet", accepted.getResponse().getHeader("fileidhash"));

    perform(post(protocol("/query")).content("{}").header("fileidhash", "delta"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesAVersionAndATimestampTogether() throws Exception {
    perform(get(protocol("/metadata?version=0&timestamp=2024-01-01T00:00:00Z")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void answersForAnIcebergTableThatTheFormatIsNotServedYet() throws Exception {
    addTable(share, "sales.forecast", "main.sales.forecast");

    // The endpoint belongs to the table, not to Delta, so asking about an Iceberg table is a
    // reasonable request this server cannot yet answer — not a bad one.
    perform(get(PROTOCOL_BASE + "/shares/" + share + "/schemas/sales/tables/forecast/metadata"))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.message").value(containsString("Iceberg REST catalog")));
  }

  @Test
  void answersForATableWhoseFormatNoOneServes() throws Exception {
    addTable(share, "sales.notes", "main.sales.notes");

    perform(get(PROTOCOL_BASE + "/shares/" + share + "/schemas/sales/tables/notes/version"))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.message").value(containsString("parquet")));
  }

  @Test
  void servesTheChangeDataFeedAsAddsChangesAndRemovals() throws Exception {
    List<JsonNode> lines = ndjson(perform(get(protocol("/changes?startingVersion=2"))).andReturn());

    assertEquals(5, lines.size(), "protocol, metaData and the three changes of commit 2");
    JsonNode removed = lineWith(lines, "remove");
    assertEquals(2, removed.get("version").asInt());
    assertEquals("NL", removed.get("partitionValues").get("country").asText());
    assertNotNull(removed.get("url").asText());

    JsonNode changed = lineWith(lines, "cdf");
    assertTrue(changed.get("url").asText().contains("_change_data"), changed.get("url").asText());
    assertTrue(changed.get("expirationTimestamp").asLong() > Instant.now().toEpochMilli());

    assertTrue(lineWith(lines, "add").get("stats").asText().contains("numRecords"));
  }

  @Test
  void spansTheWholeHistoryWhenNoWindowIsGiven() throws Exception {
    List<JsonNode> lines = ndjson(perform(get(protocol("/changes"))).andReturn());

    assertEquals(7, lines.size(), "protocol, metaData and every change across the three commits");
  }

  @Test
  void refusesToReportSchemaChangesInsideTheWindow() throws Exception {
    perform(get(protocol("/changes?includeHistoricalMetadata=true")))
        .andExpect(status().isNotImplemented());
  }

  @Test
  void refusesAChangeWindowNamedTwoWays() throws Exception {
    perform(get(protocol("/changes?startingVersion=0&startingTimestamp=2024-01-01T00:00:00Z")))
        .andExpect(status().isBadRequest());
  }

  private static JsonNode lineWith(List<JsonNode> lines, String action) {
    return lines.stream()
        .filter(line -> line.has(action))
        .map(line -> line.get(action))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no '" + action + "' line in " + lines));
  }

  private String protocol(String operation) {
    return PROTOCOL_BASE + "/shares/" + share + "/schemas/sales/tables/orders" + operation;
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(
        request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
  }

  private List<JsonNode> ndjson(MvcResult result) throws Exception {
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    List<JsonNode> lines = new ArrayList<>();
    for (String line : result.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\n")) {
      if (!line.isBlank()) {
        lines.add(json.readTree(line));
      }
    }
    return lines;
  }

  private static String fileIdsOf(List<JsonNode> lines) {
    return lines.stream()
        .filter(line -> line.has("file"))
        .map(line -> line.get("file").get("id").asText())
        .sorted()
        .reduce("", String::concat);
  }
}
