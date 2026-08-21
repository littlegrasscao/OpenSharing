package io.opensharing.principal;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A store holding rows that name a principal as owner or author. {@link PrincipalStore} asks every
 * implementation before deleting one, so a refusal can say what is still pointing at the principal
 * instead of surfacing a foreign key violation.
 *
 * <p>The question is declared here and answered elsewhere on purpose: the packages that reference a
 * principal already depend on this one, and this one never has to know them.
 */
public interface PrincipalUsage {

  /**
   * What still references the principal, phrased to be read in a sentence, or empty if nothing does.
   */
  Optional<String> describeReferencesTo(PrincipalEntity principal);

  /** {@code 2 shares}, or null when there are none, for handing to {@link #phrase}. */
  static String count(long amount, String noun) {
    return amount == 0 ? null : amount + " " + noun + (amount == 1 ? "" : "s");
  }

  /** Joins whatever was counted into one phrase, empty when nothing was. */
  static Optional<String> phrase(String... counts) {
    String joined =
        Stream.of(counts).filter(Objects::nonNull).collect(Collectors.joining(" and "));
    return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
  }
}
