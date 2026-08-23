package io.opensharing.catalog;

/**
 * Which provider-side principal a catalog request is made for, and what to authenticate to the
 * catalog as them with. Always both: every question this server asks a catalog is about an asset some
 * provider shares or is about to, so there is always somebody to name, and a catalog that decides
 * authorization has to be told who is asking in a form it can verify.
 *
 * <p>Adding an object to a share names the admin making the request, with the token it arrived with.
 * Serving a recipient names the owner of the share being read through, with the credential stored for
 * them — a recipient is nobody the catalog knows, and the owner is whose access they read by.
 *
 * <p>Both are the same secret in the end: a principal's bearer token is their catalog credential, and
 * this server keeps it sealed as well as hashed so it can be presented on their behalf once their own
 * request is over. Adding just happens to have the live one in hand.
 */
public record CatalogCaller(String name, String bearerToken) {

  public CatalogCaller {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("a catalog request is always made for a named principal");
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalArgumentException(
          "a catalog request for '" + name + "' needs a credential to make it as them");
    }
  }

  public static CatalogCaller of(String name, String bearerToken) {
    return new CatalogCaller(name, bearerToken);
  }

  /**
   * Never the credential. A record prints every component by default, and this one now travels
   * through three connector methods, so a log line or a test failure that formats the whole caller —
   * neither of which is written with a secret in mind — would print a live provider token.
   */
  @Override
  public String toString() {
    return "CatalogCaller[name=" + name + ", bearerToken=(withheld)]";
  }
}
