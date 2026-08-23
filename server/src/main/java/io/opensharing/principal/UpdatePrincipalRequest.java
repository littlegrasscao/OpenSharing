package io.opensharing.principal;

import io.opensharing.http.AdminJson;

/**
 * Only non-null fields are applied. A new bearer token invalidates the previous one immediately and
 * replaces what the server presents to the catalog as this principal, so it is also how a credential
 * is re-encrypted after the encryption key changes.
 */
@AdminJson
public record UpdatePrincipalRequest(String name, String bearerToken) {}
