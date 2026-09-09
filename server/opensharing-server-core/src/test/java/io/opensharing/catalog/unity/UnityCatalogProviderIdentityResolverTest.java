package io.opensharing.catalog.unity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opensharing.principal.Caller;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The resolver against a stub Unity Catalog answering canned responses from {@code POST
 * /opensharing/authorize}, including the two distinct ways it turns a caller away: 401 for a token
 * it does not recognize at all, and 403 for one it does recognize but whose own authorization layer
 * rejects outright (a principal with no grants, asking for something this endpoint gates) — before
 * this server's own {authorized: false} body would ever be reached.
 */
class UnityCatalogProviderIdentityResolverTest {

  private static final String BASE_PATH = "/api/2.1/unity-catalog";

  private StubUnityCatalog catalog;
  private UnityCatalogProviderIdentityResolver resolver;

  @BeforeEach
  void start() throws IOException {
    catalog = new StubUnityCatalog();
    resolver =
        new UnityCatalogProviderIdentityResolver(
            catalog.uri(), Duration.ofSeconds(2), Duration.ofSeconds(10));
  }

  @AfterEach
  void stop() {
    catalog.close();
  }

  @Test
  void resolvesAKnownBearerTokenToItsCaller() {
    catalog.answer(
        200,
        """
        {"user_id": "alice-id", "user_name": "alice@example.com", "authorized": true}
        """);

    Optional<Caller> resolved = resolver.resolve(request("Bearer alice-token"));

    assertTrue(resolved.isPresent());
    assertEquals("alice-id", resolved.get().principalId());
    assertEquals("alice@example.com", resolved.get().name());
    assertEquals("alice-token", resolved.get().bearerToken());
  }

  @Test
  void refusesARequestWithNoBearerTokenWithoutAskingTheCatalog() {
    assertTrue(resolver.resolve(request(null)).isEmpty());
    assertEquals(0, catalog.requestCount(), "nothing to ask the catalog about");
  }

  @Test
  void refusesATokenTheCatalogDoesNotRecognize() {
    catalog.answer(
        401,
        """
        {"error_code": "UNAUTHENTICATED", "message": "Invalid access token"}
        """);

    assertTrue(resolver.resolve(request("Bearer nonsense")).isEmpty());
  }

  /**
   * The catalog's own blanket check on this endpoint ({@code #principal != null}) turns away a
   * bearer token from a principal it has never heard of before this server's {authorized: false}
   * body is ever produced — as 403, not 401. Read the same as an unrecognized token, not as this
   * client failing.
   */
  @Test
  void refusesATokenTheCatalogsOwnAuthorizationLayerRejectsOutright() {
    catalog.answer(
        403,
        """
        {"error_code": "PERMISSION_DENIED", "message": "User not allowed: mallory@example.com"}
        """);

    assertTrue(resolver.resolve(request("Bearer mallory-token")).isEmpty());
  }

  @Test
  void refusesAKnownPrincipalTheCatalogSaysIsNotAuthorizedForThisRequest() {
    catalog.answer(
        200,
        """
        {"user_id": "mallory-id", "user_name": "mallory@example.com", "authorized": false}
        """);

    MockHttpServletRequest request = request("Bearer mallory-token");
    request.setMethod("POST");
    request.setRequestURI("/api/2.1/opensharing/provider/shares");

    assertTrue(resolver.resolve(request).isEmpty());
  }

  private static MockHttpServletRequest request(String authorizationHeader) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/2.1/opensharing/provider/shares/some-share");
    if (authorizationHeader != null) {
      request.addHeader("Authorization", authorizationHeader);
    }
    return request;
  }

  /** A Unity Catalog that answers whatever a test queued for {@code POST /opensharing/authorize}. */
  private static final class StubUnityCatalog implements AutoCloseable {

    private final HttpServer http;
    private String[] answer = {"404", "{\"message\": \"no stub queued\"}"};
    private int requestCount;

    StubUnityCatalog() throws IOException {
      http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      http.createContext(BASE_PATH, this::handle);
      http.start();
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + http.getAddress().getPort() + BASE_PATH);
    }

    void answer(int status, String body) {
      this.answer = new String[] {String.valueOf(status), body};
    }

    int requestCount() {
      return requestCount;
    }

    private void handle(HttpExchange exchange) throws IOException {
      requestCount++;
      byte[] bytes = answer[1].getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(Integer.parseInt(answer[0]), bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }

    @Override
    public void close() {
      http.stop(0);
    }
  }
}
