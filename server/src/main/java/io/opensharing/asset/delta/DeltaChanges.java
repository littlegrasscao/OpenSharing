package io.opensharing.asset.delta;

import java.util.List;
import java.util.Map;

/**
 * What changed in a table over a range of versions, in commit order.
 *
 * <p>Unlike a snapshot, which says what the table holds, this says what happened to it: rows added,
 * rows deleted, and — for a table with change data feed turned on — the recorded before-and-after of
 * an update, which is what a streaming reader consumes.
 *
 * @param ending the window's last version, whose schema the changes are shaped by, and which is the
 *     version the response is stamped with
 */
public record DeltaChanges(DeltaSnapshot ending, List<Change> changes) {

  public DeltaChanges {
    changes = changes == null ? List.of() : List.copyOf(changes);
  }

  /** Which of the three things a change file records, and which wire action it becomes. */
  public enum Kind {
    ADD("add"),
    CDF("cdf"),
    REMOVE("remove");

    private final String wireName;

    Kind(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }
  }

  /**
   * @param path the absolute storage path, which is what gets signed for a recipient
   * @param timestamp the commit's timestamp, not the file's, since it is the commit a reader tracks
   * @param stats the log's statistics JSON, which only added files carry
   */
  public record Change(
      Kind kind,
      String path,
      long size,
      long version,
      long timestamp,
      Map<String, String> partitionValues,
      String stats) {

    public Change {
      partitionValues = partitionValues == null ? Map.of() : Map.copyOf(partitionValues);
    }
  }
}
