package io.opensharing.catalog;

/**
 * Why an asset is being resolved. A catalog that owns authorization needs this to answer the right
 * question, since permission to put an asset into a share is not permission to read it.
 */
public enum AssetAction {
  /** Put the asset into a share, which is what a provider admin does. */
  SHARE,
  /** Read the asset's bytes, which is what a recipient ends up doing with vended credentials. */
  READ
}
