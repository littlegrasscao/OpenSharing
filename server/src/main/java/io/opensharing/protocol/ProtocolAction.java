package io.opensharing.protocol;

/**
 * The reader version a client needs to interpret the response, so a client too old for a table is
 * told rather than left to misread it.
 */
public record ProtocolAction(int minReaderVersion) {}
