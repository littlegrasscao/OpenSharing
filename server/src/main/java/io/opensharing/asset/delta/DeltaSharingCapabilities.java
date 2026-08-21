package io.opensharing.asset.delta;

import io.opensharing.http.ApiException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a client says it can handle, sent as {@code delta-sharing-capabilities} on the read
 * endpoints. The header exists so the protocol can grow without breaking older clients: a client
 * states the response formats and reader features it understands, and the server answers within
 * them or refuses.
 *
 * @param responseFormats formats the client accepts, empty meaning it never said, which the protocol
 *     defines as {@code parquet}
 * @param includeEndStreamAction whether to close the stream with an explicit final action
 */
public record DeltaSharingCapabilities(
    Set<String> responseFormats, Set<String> readerFeatures, boolean includeEndStreamAction) {

  public static final String HEADER = "delta-sharing-capabilities";
  public static final String PARQUET = "parquet";
  public static final String DELTA = "delta";

  private static final String RESPONSE_FORMAT = "responseformat";
  private static final String READER_FEATURES = "readerfeatures";
  private static final String INCLUDE_END_STREAM_ACTION = "includeendstreamaction";

  public DeltaSharingCapabilities {
    responseFormats = responseFormats == null ? Set.of() : Set.copyOf(responseFormats);
    readerFeatures = readerFeatures == null ? Set.of() : Set.copyOf(readerFeatures);
  }

  /** A client that sent no header, which the protocol says means parquet and no end-stream action. */
  public static DeltaSharingCapabilities defaults() {
    return new DeltaSharingCapabilities(Set.of(), Set.of(), false);
  }

  /** Keys and values are case-insensitive, and anything unrecognised is ignored, as the spec says. */
  public static DeltaSharingCapabilities parse(String header) {
    if (header == null || header.isBlank()) {
      return defaults();
    }
    Set<String> formats = new LinkedHashSet<>();
    Set<String> features = new LinkedHashSet<>();
    boolean endStream = false;
    for (String capability : header.split(";")) {
      String[] parts = capability.split("=", 2);
      if (parts.length != 2) {
        continue;
      }
      String key = parts[0].trim().toLowerCase(Locale.ROOT);
      String value = parts[1].trim();
      switch (key) {
        case RESPONSE_FORMAT -> formats.addAll(values(value));
        case READER_FEATURES -> features.addAll(values(value));
        case INCLUDE_END_STREAM_ACTION -> endStream = Boolean.parseBoolean(value);
        default -> {
          // Unrecognised capabilities are ignored so a newer client still gets an answer.
        }
      }
    }
    return new DeltaSharingCapabilities(formats, features, endStream);
  }

  /**
   * This server answers in parquet format only. A client that asked for delta format alone is told
   * so rather than handed a response it said it could not read.
   */
  public void requireParquetIsAcceptable() {
    if (!responseFormats.isEmpty() && !responseFormats.contains(PARQUET)) {
      throw ApiException.notImplemented(
          "this server answers in parquet response format, which this request excludes ("
              + String.join(",", responseFormats)
              + ")");
    }
  }

  /** Echoed back so a client can see which format it is holding. */
  public String responseHeaderValue() {
    return RESPONSE_FORMAT + "=" + PARQUET;
  }

  private static Set<String> values(String value) {
    return Arrays.stream(value.split(","))
        .map(part -> part.trim().toLowerCase(Locale.ROOT))
        .filter(part -> !part.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
