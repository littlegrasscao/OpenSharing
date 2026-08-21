package io.opensharing.asset.storage;

/**
 * Whether a path is one of a shared table's own.
 *
 * <p>Every grant this server hands out is scoped to a location, so a path outside that location is
 * one the provider never shared — and the two places that can be handed such a path, a Delta log
 * naming a file and a catalog naming an Iceberg metadata document, are equally able to name one by
 * accident or on purpose. They ask the same question here so that neither is stricter than the
 * other, and each says its own refusal, which is the only part that differs.
 */
public final class StoragePaths {

  private StoragePaths() {}

  /** Whether a path resolved against a root stays under it. */
  public static boolean isInside(String path, String root) {
    if (path == null || root == null || root.isBlank()) {
      return false;
    }
    String prefix = root.endsWith("/") ? root : root + "/";
    return path.startsWith(prefix) && !climbsOut(path);
  }

  /**
   * Whether a path steps back up out of wherever it starts. Only a whole segment counts: a file
   * named {@code v2..metadata.json} climbs nowhere, and refusing it would refuse a name storage
   * allows.
   */
  public static boolean climbsOut(String path) {
    for (String segment : path.split("/")) {
      if (segment.equals("..")) {
        return true;
      }
    }
    return false;
  }
}
