package io.opensharing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensharing.http.ErrorCodes;
import io.opensharing.http.ErrorResponse;
import io.opensharing.principal.Caller;
import io.opensharing.principal.PrincipalEntity;
import io.opensharing.principal.PrincipalStore;
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
 * <p>Principals are provisioned from {@code opensharing.principals} at startup rather than through
 * an API, so every request here must present one of those bearer tokens.
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

  private final PrincipalStore principals;
  private final ObjectMapper objectMapper;

  public AdminAuthenticationFilter(PrincipalStore principals, ObjectMapper objectMapper) {
    this.principals = principals;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
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

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), new ErrorResponse(ErrorCodes.UNAUTHENTICATED, message));
  }
}
