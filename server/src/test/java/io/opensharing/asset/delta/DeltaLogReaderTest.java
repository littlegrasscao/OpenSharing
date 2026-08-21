package io.opensharing.asset.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opensharing.http.ApiException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads the three-commit Delta table under {@code src/test/resources/delta-table}: an append, a
 * second append, then an update that removes a file, adds its replacement and records the change. A
 * hand-written log is enough because nothing here opens a data file: the server lists them and signs
 * urls, and the bytes are the recipient's business.
 */
class DeltaLogReaderTest {

  private static final DeltaLogReader READER = new DeltaLogReader();

  private static String tableRoot() throws IOException {
    return tableRoot("orders");
  }

  private static String tableRoot(String name) throws IOException {
    return new ClassPathResource("delta-table/" + name).getFile().getAbsolutePath();
  }

  private static long commitTime(String table, String commit) throws IOException {
    return new ClassPathResource("delta-table/" + table + "/_delta_log/" + commit + ".json")
        .getFile()
        .lastModified();
  }

  @Test
  void readsTheLatestVersionAndItsMetadata() throws Exception {
    DeltaSnapshot snapshot = READER.read(tableRoot(), null, DeltaVersion.latest(), false);

    assertEquals(2, snapshot.version());
    assertEquals(1, snapshot.protocol().minReaderVersion());
    assertEquals("11111111-2222-3333-4444-555555555555", snapshot.metadata().id());
    assertEquals("parquet", snapshot.metadata().formatProvider());
    assertEquals(List.of("country"), snapshot.metadata().partitionColumns());
    assertEquals(
        "true", snapshot.metadata().configuration().get("delta.enableChangeDataFeed"));
    assertTrue(snapshot.metadata().schemaString().contains("order_id"));
    assertTrue(snapshot.files().isEmpty(), "files are only listed when asked for");
    assertEquals(
        commitTime("orders", "00000000000000000002"),
        snapshot.timestamp(),
        "the version's commit time, which is what a client is told beside a version");
  }

  @Test
  void keepsThePartitionColumnsAsTheLogNamesAndOrdersThem() throws Exception {
    DeltaSnapshot snapshot =
        READER.read(tableRoot("inventory"), null, DeltaVersion.latest(), false);

    assertEquals(
        List.of("Country", "Region"),
        snapshot.metadata().partitionColumns(),
        "the log's own case and order, not the schema's order or a lower-cased copy");
  }

  @Test
  void refusesToListAFileTheTableDoesNotOwn() throws Exception {
    String root = tableRoot("cloned");
    ApiException e =
        assertThrows(
            ApiException.class, () -> READER.read(root, null, DeltaVersion.latest(), true));

    assertEquals(501, e.getStatus().value());
    assertTrue(e.getMessage().contains("outside the table's own directory"), e.getMessage());
  }

  @Test
  void refusesAPathThatClimbsOutOfTheTable() {
    ApiException e =
        assertThrows(
            ApiException.class, () -> DeltaLogReader.requireInsideTable("../orders/part-0.parquet"));

    assertEquals(501, e.getStatus().value());
  }

  @Test
  void decodesAPathTheLogWroteEscaped() {
    assertEquals(
        "country=New Zealand/part-0.parquet",
        DeltaLogReader.requireInsideTable("country=New%20Zealand/part-0.parquet"));
  }

  @Test
  void listsOnlyTheFilesStillActiveAtThatVersion() throws Exception {
    DeltaSnapshot snapshot = READER.read(tableRoot(), null, DeltaVersion.latest(), true);

    assertEquals(2, snapshot.files().size(), "the file removed in commit 2 is gone");
    DeltaSnapshot.File file =
        snapshot.files().stream()
            .filter(f -> f.path().contains("country=DE"))
            .findFirst()
            .orElseThrow();
    assertEquals(2345, file.size());
    assertEquals("DE", file.partitionValues().get("country"));
    assertTrue(file.stats().contains("numRecords"));
    assertTrue(file.path().startsWith("file:") || file.path().startsWith("/"));
  }

  @Test
  void readsTheChangeFeedAsAddsRemovesAndRecordedChanges() throws Exception {
    DeltaChanges changes = READER.changes(tableRoot(), null, 2, 2);

    assertEquals(3, changes.changes().size());
    assertEquals(
        List.of(DeltaChanges.Kind.ADD, DeltaChanges.Kind.CDF, DeltaChanges.Kind.REMOVE),
        changes.changes().stream().map(DeltaChanges.Change::kind).sorted().toList(),
        "commit 2 removed a file, added its replacement and recorded the change");
    DeltaChanges.Change cdf =
        changes.changes().stream()
            .filter(change -> change.kind() == DeltaChanges.Kind.CDF)
            .findFirst()
            .orElseThrow();
    assertTrue(cdf.path().contains("_change_data/"), cdf.path());
    assertEquals(2, cdf.version());
    assertEquals("NL", cdf.partitionValues().get("country"));
  }

  @Test
  void spansEveryVersionInTheChangeWindow() throws Exception {
    DeltaChanges changes = READER.changes(tableRoot(), null, 0, 2);

    assertEquals(5, changes.changes().size(), "two appends, then a remove, an add and a change");
    assertEquals(0, changes.changes().get(0).version());
  }

  @Test
  void refusesABackwardsChangeWindow() throws Exception {
    String root = tableRoot();
    ApiException e = assertThrows(ApiException.class, () -> READER.changes(root, null, 2, 1));

    assertEquals(400, e.getStatus().value());
  }

  @Test
  void readsAnEarlierVersion() throws Exception {
    DeltaSnapshot snapshot = READER.read(tableRoot(), null, DeltaVersion.of(0), true);

    assertEquals(0, snapshot.version());
    assertEquals(1, snapshot.files().size());
  }

  @Test
  void reportsAVersionTheTableNeverHadAsABadRequest() throws Exception {
    String root = tableRoot();
    ApiException e =
        assertThrows(
            ApiException.class, () -> READER.read(root, null, DeltaVersion.of(99), false));

    assertEquals(400, e.getStatus().value());
  }

  @Test
  void reportsAMissingLogAsNotFound() {
    ApiException e =
        assertThrows(
            ApiException.class,
            () -> READER.read("/tmp/not-a-delta-table", null, DeltaVersion.latest(), false));

    assertEquals(404, e.getStatus().value());
  }

  @Test
  void addressesS3TheWayHadoopDoes() {
    assertEquals("s3a://bucket/table", DeltaLogReader.hadoopPath("s3://bucket/table/"));
    assertEquals("abfss://c@a.dfs.core.windows.net/t", DeltaLogReader.hadoopPath("abfss://c@a.dfs.core.windows.net/t"));
  }
}
