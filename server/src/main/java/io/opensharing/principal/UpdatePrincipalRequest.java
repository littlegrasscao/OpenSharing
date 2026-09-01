package io.opensharing.principal;

import io.opensharing.http.AdminJson;

/** Only non-null fields are applied. A new bearer token invalidates the previous one immediately. */
@AdminJson
public record UpdatePrincipalRequest(String name, String bearerToken) {}
