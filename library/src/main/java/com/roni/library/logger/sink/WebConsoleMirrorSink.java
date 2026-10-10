package com.roni.library.logger.sink;

import com.roni.library.contracts.logger.LogEntry;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Mirrors Web Application console.log/info/warn/error into the native log sinks.
 */
public class WebConsoleMirrorSink implements ILogSink {
    public interface WebLogListener {
        void onWebLogCaptured(LogEntry entry);
    }

    private final String name;
    private volatile boolean enabled = true;
    private volatile boolean disposed = false;
    private final List<WebLogListener> listeners = new CopyOnWriteArrayList<>();

    public WebConsoleMirrorSink(String name) {
        this.name = name != null ? name : "WebConsoleMirrorSink";
    }

    public WebConsoleMirrorSink() {
        this("WebConsoleMirrorSink");
    }

    public void addListener(WebLogListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(WebLogListener listener) {
        listeners.remove(listener);
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

        // Only mirror entries originating from web or bridge
        if ("WebWorker".equalsIgnoreCase(entry.getTag()) || "Console".equalsIgnoreCase(entry.getTag())) {
            for (WebLogListener listener : listeners) {
                try {
                    listener.onWebLogCaptured(entry);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void flush() {}

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
        listeners.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
