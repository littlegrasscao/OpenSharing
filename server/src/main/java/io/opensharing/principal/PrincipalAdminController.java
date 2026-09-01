package io.opensharing.principal;

import io.opensharing.http.ListResponse;
import io.opensharing.http.Listings;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider-admin API for the principals that administer sharing. Registration is reserved to the
 * bootstrap administrator token; reading, updating and deleting a principal are ordinary admin calls
 * that authenticate as a principal. {@link io.opensharing.auth.AdminAuthenticationFilter} enforces
 * that split.
 */
@RestController
@RequestMapping("${opensharing.admin.base-path}/principals")
public class PrincipalAdminController {

  private final PrincipalStore principals;
  private final Listings listings;

  public PrincipalAdminController(PrincipalStore principals, Listings listings) {
    this.principals = principals;
    this.listings = listings;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PrincipalResponse create(@Valid @RequestBody CreatePrincipalRequest request) {
    return PrincipalResponse.from(
        principals.create(
            request.id(), request.type(), request.name(), request.bearerToken()));
  }

  @GetMapping
  public ListResponse<PrincipalResponse> list(
      @RequestParam(required = false) Integer maxResults,
      @RequestParam(required = false) String pageToken) {
    return listings.page(maxResults, pageToken, principals::list, PrincipalResponse::from);
  }

  @GetMapping("/{principal}")
  public PrincipalResponse get(@PathVariable String principal) {
    return PrincipalResponse.from(principals.require(principal));
  }

  @PatchMapping("/{principal}")
  public PrincipalResponse update(
      @PathVariable String principal, @RequestBody UpdatePrincipalRequest request) {
    return PrincipalResponse.from(
        principals.update(principal, request.name(), request.bearerToken()));
  }

  @DeleteMapping("/{principal}")
  public ResponseEntity<Void> delete(@PathVariable String principal) {
    principals.delete(principal);
    return ResponseEntity.noContent().build();
  }
}
