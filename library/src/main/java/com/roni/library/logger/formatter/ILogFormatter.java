package com.roni.library.logger.formatter;

import com.roni.library.contracts.logger.LogEntry;

/**
 * Strategy interface for formatting a structured LogEntry into string representation.
 */
public interface ILogFormatter {
    /**
     * Formats the given log entry.
     *
     * @param entry the structured log entry.
     * @return formatted string representation.
     */
    String format(LogEntry entry);
}
