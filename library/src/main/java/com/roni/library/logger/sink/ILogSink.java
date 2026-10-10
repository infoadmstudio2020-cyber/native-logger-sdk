package com.roni.library.logger.sink;

import com.roni.library.contracts.common.IDisposable;
import com.roni.library.contracts.logger.LogEntry;

/**
 * Standard pluggable output target for structured log records.
 */
public interface ILogSink extends IDisposable {
    /**
     * Unique identifier for this sink.
     */
    String getName();

    /**
     * Writes the log entry to the destination sink.
     */
    void write(LogEntry entry);

    /**
     * Flushes buffered log data to underlying storage or network.
     */
    void flush();

    /**
     * Returns true if this sink is active and accepting logs.
     */
    boolean isEnabled();

    /**
     * Enables or disables this sink dynamically.
     */
    void setEnabled(boolean enabled);
}
