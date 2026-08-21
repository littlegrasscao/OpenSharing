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
 * Authenticates a provider-admin request and decides which of the two credentials may make it.
 *
 * <p>Registering a principal is the bootstrap administrator token's only privilege, and its exclusive
 * one: a principal cannot register another principal, and the bootstrap token cannot touch anything
 * else. Bootstrapping is therefore a step an operator takes once, holding a token that would be
 * useless to steal for any other purpose.
 *
 * <p>Every other request resolves to a {@link Caller}, so downstream code can record who owns and who
 * authored what the request creates.
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

  private final PrincipalStore principals;
  private final String bootstrapToken;
  private final String registrationPath;
  private final ObjectMapper objectMapper;

  public AdminAuthenticationFilter(
      PrincipalStore principals,
      String bootstrapToken,
      String adminBasePath,
      ObjectMapper objectMapper) {
    this.principals = principals;
    this.bootstrapToken = bootstrapToken;
    this.registrationPath = trimTrailingSlash(adminBasePath) + "/principals";
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
    String token = presented.get();
    boolean registration = isPrincipalRegistration(request);

    if (Secrets.constantTimeEquals(token, bootstrapToken)) {
      if (!registration) {
        forbid(
            response,
            "the bootstrap administrator token may only POST "
                + registrationPath
                + "; register a principal and authenticate as it instead");
        return;
      }
      chain.doFilter(request, response);
      return;
    }

    Optional<PrincipalEntity> principal = principals.findByToken(token);
    if (principal.isEmpty()) {
      reject(response, "the bearer token does not belong to a known principal");
      return;
    }
    if (registration) {
      forbid(response, "only the bootstrap administrator token may register principals");
      return;
    }
    request.setAttribute(Caller.REQUEST_ATTRIBUTE, Caller.of(principal.get(), token));
    chain.doFilter(request, response);
  }

  private boolean isPrincipalRegistration(HttpServletRequest request) {
    return "POST".equalsIgnoreCase(request.getMethod())
        && registrationPath.equals(trimTrailingSlash(request.getRequestURI()));
  }

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    body(response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, message);
  }

  private void forbid(HttpServletResponse response, String message) throws IOException {
    body(response, HttpStatus.FORBIDDEN, ErrorCodes.PERMISSION_DENIED, message);
  }

  private void body(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
  }

  private static String trimTrailingSlash(String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }
}
