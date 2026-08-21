package io.opensharing.asset;

/**
 * Whether a shared object can still be served. An object is ACTIVE when it is added, and moves off
 * ACTIVE when the catalog later stops resolving it. Only ACTIVE objects appear to recipients.
 */
public enum SharedObjectStatus {
  ACTIVE,
  /** The catalog refuses to resolve the object for the sharing server. */
  PERMISSION_DENIED,
  /** The object was dropped or renamed in the catalog after it was shared. */
  SOURCE_NOT_FOUND
}
