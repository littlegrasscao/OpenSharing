package io.opensharing.principal;

import io.opensharing.http.AdminJson;
import jakarta.validation.constraints.NotBlank;

/**
 * @param id the UUID to register the principal under, or null to have the server generate one. Giving
 *     it lets a principal keep the id it already has in an external directory.
 * @param bearerToken the principal's catalog credential, which is also what they authenticate to this
 *     server with. One secret doing both jobs is deliberate: the server asks the catalog as whoever
 *     shared an asset, so it has to hold something the catalog accepts as them, and a second secret
 *     would only have to be kept identical to this one to behave. Never returned or logged.
 */
@AdminJson
public record CreatePrincipalRequest(
    String id, PrincipalType type, @NotBlank String name, @NotBlank String bearerToken) {

  public CreatePrincipalRequest {
    type = type == null ? PrincipalType.USER : type;
  }
}
