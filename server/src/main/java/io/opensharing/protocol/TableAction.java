package io.opensharing.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One line of a Delta read response. The protocol sends newline-delimited JSON where each line is
 * this wrapper carrying exactly one action, so the factories are the only way to build one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableAction(
    ProtocolAction protocol,
    MetadataAction metaData,
    FileAction file,
    ChangeFileAction add,
    ChangeFileAction cdf,
    ChangeFileAction remove,
    EndStreamAction endStreamAction) {

  public static TableAction of(ProtocolAction protocol) {
    return new TableAction(protocol, null, null, null, null, null, null);
  }

  public static TableAction of(MetadataAction metadata) {
    return new TableAction(null, metadata, null, null, null, null, null);
  }

  public static TableAction of(FileAction file) {
    return new TableAction(null, null, file, null, null, null, null);
  }

  public static TableAction added(ChangeFileAction change) {
    return new TableAction(null, null, null, change, null, null, null);
  }

  public static TableAction changed(ChangeFileAction change) {
    return new TableAction(null, null, null, null, change, null, null);
  }

  public static TableAction removed(ChangeFileAction change) {
    return new TableAction(null, null, null, null, null, change, null);
  }

  public static TableAction of(EndStreamAction endStream) {
    return new TableAction(null, null, null, null, null, null, endStream);
  }
}
