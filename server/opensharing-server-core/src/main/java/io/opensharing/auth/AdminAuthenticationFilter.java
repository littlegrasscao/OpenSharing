package io.opensharing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.http.ErrorCodes;
import io.opensharing.http.ErrorResponse;
import io.opensharing.principal.Caller;
import io.opensharing.principal.PrincipalEntity;
import io.opensharing.principal.PrincipalStore;
import io.opensharing.principal.PrincipalType;
import io.opensharing.runtime.ProviderIdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a provider-admin request as a configured principal.
 *
 * <p>Principals are provisioned from {@code opensharing.admin.principals} at startup rather than
 * than through an admin API, so every request here must present one of those bearer tokens.
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

  private final PrincipalStore principals;
  private final ProviderIdentityResolver identityResolver;
  private final ObjectMapper objectMapper;

  public AdminAuthenticationFilter(
      PrincipalStore principals,
      ObjectMapper objectMapper,
      ProviderIdentityResolver identityResolver) {
    this.principals = principals;
    this.objectMapper = objectMapper;
    this.identityResolver = identityResolver;
  }

  public AdminAuthenticationFilter(PrincipalStore principals, ObjectMapper objectMapper) {
    this(principals, objectMapper, null);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (identityResolver != null) {
      Optional<Caller> resolved = identityResolver.resolve(request);
      if (resolved.isPresent()) {
        request.setAttribute(Caller.REQUEST_ATTRIBUTE, provisioned(resolved.get()));
        chain.doFilter(request, response);
        return;
      }
    }
    Optional<String> presented = BearerTokens.from(request);
    if (presented.isEmpty()) {
      reject(response, "a provider-admin bearer token is required");
      return;
    }
    Optional<PrincipalEntity> principal = principals.findByToken(presented.get());
    if (principal.isEmpty()) {
      reject(response, "the bearer token does not belong to a known principal");
      return;
    }
    request.setAttribute(Caller.REQUEST_ATTRIBUTE, Caller.of(principal.get(), presented.get()));
    chain.doFilter(request, response);
  }

  /**
   * The host resolves a principal's identity, not its row in this server's own store. Provisioning
   * it here — same as a configured {@code opensharing.admin.principals} entry — is what lets {@link
   * io.opensharing.principal.PrincipalStore#require} find it by id later in the same request, and
   * keeps its stored catalog credential current with whatever the host just presented.
   */
  private Caller provisioned(Caller resolved) {
    PrincipalEntity principal =
        principals.provision(PrincipalType.USER, resolved.name(), resolved.bearerToken());
    return Caller.of(principal, resolved.bearerToken());
  }

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), new ErrorResponse(ErrorCodes.UNAUTHENTICATED, message));
  }
}
