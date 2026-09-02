package io.opensharing.auth;

/** How a principal's catalog credential is obtained and presented on catalog API calls. */
public enum CatalogAuthType {
  /** A static bearer token configured in {@code opensharing.principals}. */
  TOKEN,
  /** Tokens from an external identity provider. Not implemented yet. */
  OIDC
}
