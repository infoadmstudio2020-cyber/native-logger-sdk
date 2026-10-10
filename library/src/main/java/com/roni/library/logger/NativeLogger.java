package com.roni.library.logger;

import com.roni.library.contracts.common.IDisposable;
import com.roni.library.contracts.logger.ILogger;
import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.sink.FileLogSink;
import com.roni.library.logger.sink.ILogSink;
import com.roni.library.logger.sink.LogcatSink;
import com.roni.library.logger.sink.WebConsoleMirrorSink;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enterprise non-blocking asynchronous structured logging engine.
 * Implements ILogger and IDisposable with zero UI stutter guarantees.
 */
public class NativeLogger implements ILogger, IDisposable {
    private static volatile NativeLogger instance;
    private static final Object LOCK = new Object();

    private final LoggerConfig config;
    private volatile LogLevel minimumLevel;
    private final List<ILogSink> sinks = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<LogEntry> asyncQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final Thread dispatcherThread;

    public NativeLogger(LoggerConfig config) {
        this.config = config != null ? config : LoggerConfig.builder().build();
        this.minimumLevel = this.config.getMinimumLevel();

        // 1. Mount standard Logcat sink
        this.sinks.add(new LogcatSink("DefaultLogcat", this.config.getCustomFormatter()));

        // 2. Mount File sink if configured
        if (this.config.isFileLoggingEnabled() && this.config.getLogDirectory() != null) {
            this.sinks.add(new FileLogSink(this.config.getLogDirectory()));
        }

        // 3. Mount Web Console sink if enabled
        if (this.config.isConsoleMirroringEnabled()) {
            this.sinks.add(new WebConsoleMirrorSink());
        }

        // 4. Mount additional user-specified sinks
        this.sinks.addAll(this.config.getAdditionalSinks());

        // 5. Initialize background worker daemon thread
        this.dispatcherThread = new Thread(this::dispatchLoop, "NativeLogger-Dispatcher");
        this.dispatcherThread.setDaemon(true);
        this.dispatcherThread.start();
    }

    public static NativeLogger init(LoggerConfig config) {
        synchronized (LOCK) {
            if (instance != null) {
                instance.dispose();
            }
            instance = new NativeLogger(config);
            return instance;
        }
    }

    public static NativeLogger getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new NativeLogger(LoggerConfig.builder().build());
                }
            }
        }
        return instance;
    }

    private void dispatchLoop() {
        while (isRunning.get() || !asyncQueue.isEmpty()) {
            LogEntry entry = asyncQueue.poll();
            if (entry != null) {
                for (ILogSink sink : sinks) {
                    try {
                        sink.write(entry);
                    } catch (Throwable t) {
                        System.err.println("Sink " + sink.getName() + " write failed: " + t.getMessage());
                    }
                }
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    if (!isRunning.get()) break;
                }
            }
        }
    }

    @Override
    public void setMinimumLevel(LogLevel level) {
        if (level != null) {
            this.minimumLevel = level;
        }
    }

    @Override
    public LogLevel getMinimumLevel() {
        return minimumLevel;
    }

    public void addSink(ILogSink sink) {
        if (sink != null && !sinks.contains(sink)) {
            sinks.add(sink);
        }
    }

    public void removeSink(ILogSink sink) {
        if (sink != null) {
            sinks.remove(sink);
            sink.dispose();
        }
    }

    public List<ILogSink> getSinks() {
        return sinks;
    }

    @Override
    public void log(LogEntry entry) {
        if (entry == null || isDisposed.get()) {
            return;
        }

        if (entry.getLevel().isLoggable(minimumLevel)) {
            asyncQueue.offer(entry);
        }
    }

    @Override
    public void v(String tag, String message) {
        log(new LogEntry(LogLevel.VERBOSE, tag, message));
    }

    @Override
    public void d(String tag, String message) {
        log(new LogEntry(LogLevel.DEBUG, tag, message));
    }

    @Override
    public void i(String tag, String message) {
        log(new LogEntry(LogLevel.INFO, tag, message));
    }

    @Override
    public void w(String tag, String message) {
        log(new LogEntry(LogLevel.WARN, tag, message));
    }

    @Override
    public void w(String tag, String message, Throwable throwable) {
        log(new LogEntry(System.currentTimeMillis(), LogLevel.WARN, tag, message, Thread.currentThread().getName(), throwable, ""));
    }

    @Override
    public void e(String tag, String message) {
        log(new LogEntry(LogLevel.ERROR, tag, message));
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
        log(new LogEntry(System.currentTimeMillis(), LogLevel.ERROR, tag, message, Thread.currentThread().getName(), throwable, ""));
    }

    public void flush() {
        // Drain current queue
        LogEntry entry;
        while ((entry = asyncQueue.poll()) != null) {
            for (ILogSink sink : sinks) {
                try {
                    sink.write(entry);
                } catch (Throwable ignored) {}
            }
        }

        for (ILogSink sink : sinks) {
            try {
                sink.flush();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            isRunning.set(false);
            dispatcherThread.interrupt();
            flush();
            for (ILogSink sink : sinks) {
                try {
                    sink.dispose();
                } catch (Throwable ignored) {}
            }
            sinks.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return isDisposed.get();
    }
}
