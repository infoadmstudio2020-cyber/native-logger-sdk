package com.roni.library.logger.sink;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Batches high-severity diagnostic entries (WARN, ERROR, ASSERT) for diagnostic upload.
 */
public class RemoteDiagnosticSink implements ILogSink {
    public interface DiagnosticUploader {
        void uploadBatch(List<LogEntry> entries);
    }

    private final String name;
    private final int batchSize;
    private final DiagnosticUploader uploader;
    private final ConcurrentLinkedQueue<LogEntry> diagnosticQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean enabled = true;
    private volatile boolean disposed = false;

    public RemoteDiagnosticSink(String name, int batchSize, DiagnosticUploader uploader) {
        this.name = name != null ? name : "RemoteDiagnosticSink";
        this.batchSize = batchSize > 0 ? batchSize : 50;
        this.uploader = uploader;
    }

    public RemoteDiagnosticSink(DiagnosticUploader uploader) {
        this("RemoteDiagnosticSink", 50, uploader);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void write(LogEntry entry) {
        if (!enabled || disposed || entry == null) {
            return;
        }

        // Capture only high severity logs
        if (entry.getLevel() == LogLevel.WARN || entry.getLevel() == LogLevel.ERROR || entry.getLevel() == LogLevel.ASSERT) {
            diagnosticQueue.add(entry);
            if (diagnosticQueue.size() >= batchSize) {
                flush();
            }
        }
    }

    @Override
    public void flush() {
        if (uploader == null || diagnosticQueue.isEmpty()) {
            return;
        }

        List<LogEntry> batch = new ArrayList<>();
        LogEntry item;
        while ((item = diagnosticQueue.poll()) != null && batch.size() < batchSize) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                uploader.uploadBatch(batch);
            } catch (Throwable e) {
                System.err.println("RemoteDiagnosticSink upload batch failed: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void dispose() {
        disposed = true;
        flush();
        diagnosticQueue.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public int getQueueSize() {
        return diagnosticQueue.size();
    }
}
