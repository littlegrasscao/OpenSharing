package io.opensharing.principal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.opensharing.http.AdminJson;
import java.time.Instant;

/** A principal as returned by the admin API. The bearer token is deliberately absent. */
@AdminJson
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrincipalResponse(
    String id, PrincipalType type, String name, Instant createdAt, Instant updatedAt) {

  public static PrincipalResponse from(PrincipalEntity principal) {
    return new PrincipalResponse(
        principal.getId(),
        principal.getType(),
        principal.getName(),
        principal.getCreatedAt(),
        principal.getUpdatedAt());
  }
}
