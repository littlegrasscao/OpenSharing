package io.opensharing.principal;

import io.opensharing.http.AdminJson;
import jakarta.validation.constraints.NotBlank;

/**
 * @param id the UUID to register the principal under, or null to have the server generate one. Giving
 *     it lets a principal keep the id it already has in an external directory.
 * @param bearerToken the secret the principal will authenticate with, chosen by the caller. Only its
 *     hash is stored, and the server never returns or logs the value.
 */
@AdminJson
public record CreatePrincipalRequest(
    String id, PrincipalType type, @NotBlank String name, @NotBlank String bearerToken) {

  public CreatePrincipalRequest {
    type = type == null ? PrincipalType.USER : type;
  }
}
