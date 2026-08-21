package io.opensharing.asset.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The one rule about whether a path is a shared table's own, asked by every format. */
class StoragePathsTest {

  private static final String ROOT = "s3a://lake/sales/orders";

  @Test
  void acceptsWhatIsUnderTheTablesOwnRoot() {
    assertTrue(StoragePaths.isInside(ROOT + "/country=NL/part-0.parquet", ROOT));
    assertTrue(
        StoragePaths.isInside(ROOT + "/metadata/v2.metadata.json", ROOT + "/"),
        "a root the catalog wrote with a trailing slash names the same directory");
  }

  @Test
  void refusesWhatIsBesideItOrAboveIt() {
    assertFalse(StoragePaths.isInside("s3a://lake/sales/payroll/part-0.parquet", ROOT));
    assertFalse(
        StoragePaths.isInside("s3a://lake/sales/orders-archive/part-0.parquet", ROOT),
        "a sibling whose name merely starts the same way is not inside");
    assertFalse(StoragePaths.isInside(ROOT + "/../payroll/part-0.parquet", ROOT));
    assertFalse(StoragePaths.isInside(null, ROOT));
    assertFalse(StoragePaths.isInside(ROOT + "/part-0.parquet", null));
  }

  /** Only a whole segment climbs; a file whose name happens to hold two dots does not. */
  @Test
  void readsOnlyAWholeSegmentAsClimbingOut() {
    assertTrue(StoragePaths.climbsOut("orders/../payroll/part-0.parquet"));
    assertFalse(StoragePaths.climbsOut("orders/metadata/v2..metadata.json"));
    assertFalse(StoragePaths.climbsOut("orders/part-0.parquet"));
  }
}
